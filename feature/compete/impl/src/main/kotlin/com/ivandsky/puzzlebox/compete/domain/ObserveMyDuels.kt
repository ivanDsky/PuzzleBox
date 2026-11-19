package com.ivandsky.puzzlebox.compete.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LocalMatch
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.data.match.isSelf
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/**
 * The read model behind the Duels tab rows and the Play-tab badge. One `observeMine` listener, one `observe(code)`
 * listener per cached link invite that is still open, the local cache and my outbox; it never reads a sealed result.
 * Rows needing me come first, then by the nearest deadline. A duel whose reveal this device has seen stays as a
 * [DuelRowKind.Finished] row after every active one, newest seen first, for seven days after it was first seen;
 * the score it shows is the one this device stored when it revealed the duel, so a finished duel is never counted as
 * needing me. The list re-emits at each future time boundary (`closesAt`, `deadline`, and the end of a finished
 * row's stay) with no upstream change. Past `deadline` a match has no move left to ask for (a started puzzle is
 * resumed from the duel screen) and an unaccepted invite is dropped, so neither needs me.
 * Each row carries how many puzzles the opponent has played ([DuelRow.theirDone], from the public progress), never a
 * time, and how many I have: my outbox rows plus my own public progress, so a puzzle played on another device of mine
 * counts without a read. Signed out, or an `observeMine` of null, has no friend rows. Writes nothing.
 * Self duels (the device store, signed in or out) are listed beside them as rows with [DuelRow.isSelf]: seat
 * [SelfSeats.ME], the same kinds and the same seven-day Finished stay, but they sort after the friend rows of their
 * section, never count in [MyDuels.needYou] and never schedule a time boundary (they have no deadline).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMyDuels(
    private val repo: MatchRepository,
    private val self: SelfMatchRepository,
    private val outbox: MatchResultOutbox,
    private val local: MatchLocalRepository,
    private val identity: IdentityService,
    private val clock: () -> Instant,
) {

    private class Inputs(val mine: List<Match>?, val selfs: List<Match>, val locals: List<LocalMatch>, val outbox: List<OutboxResult>)

    operator fun invoke(): Flow<MyDuels> =
        identity.current.map { it?.uid }.distinctUntilChanged().flatMapLatest { me -> observeAs(me) }

    private fun observeAs(me: String?): Flow<MyDuels> = flow {
        val inputs = MutableStateFlow<Inputs?>(null)
        coroutineScope {
            launch {
                combine(server(me), self.observeAll(), local.observeAll(), outbox.observeAll(), ::Inputs).collect { inputs.value = it }
            }
            val latest = inputs.filterNotNull()
            val invites = latest.map { inviteCodes(me, it) }.distinctUntilChanged().flatMapLatest { codes ->
                if (codes.isEmpty()) flowOf(emptyMap()) else combine(codes.map { watched(it) }) { it.toMap() }
            }
            emitAll(
                combine(latest, invites) { now, fetched -> now to fetched }.transformLatest { (now, fetched) ->
                    val mine = now.mine.orEmpty()
                    var at = clock()
                    val candidates = mine + fetched.values.mapNotNull { (it as? MatchFetch.Found)?.match }
                    emit(duels(me, now, mine, fetched, at))
                    var next = nextBoundary(candidates, now.locals, at)
                    while (next != null) {
                        delay(Duration.between(at, next).toKotlinDuration())
                        at = clock()
                        emit(duels(me, now, mine, fetched, at))
                        next = nextBoundary(candidates, now.locals, at)
                    }
                }.distinctUntilChanged(),
            )
        }
    }

    /** The server's duels of [me]; none signed out. */
    private fun server(me: String?): Flow<List<Match>?> = if (me == null) flowOf(emptyList()) else repo.observeMine()

    /** Cached link invites of mine that no server row covers, so each needs its own fetch. */
    private fun inviteCodes(me: String?, inputs: Inputs): Set<ShareCode> {
        val mine = inputs.mine ?: return emptySet()
        if (me == null) return emptySet()
        val served = mine.mapTo(HashSet()) { it.code }
        return inputs.locals.filter { it.role == LocalRole.Invite && !it.dismissed && it.code !in served }
            .mapTo(LinkedHashSet()) { it.code }
    }

    /**
     * One invite's match, then its `closesAt` re-checked against the clock; the listener ends once the match has
     * closed, keeping its last value (the row is dropped by time).
     */
    private fun watched(code: ShareCode): Flow<Pair<ShareCode, MatchFetch>> =
        repo.observe(code).transformLatest { fetch ->
            emit(fetch)
            if (fetch is MatchFetch.Found) {
                val wait = Duration.between(clock(), fetch.match.closesAt)
                if (!wait.isNegative && !wait.isZero) {
                    delay(wait.toKotlinDuration())
                    emit(fetch)
                }
            }
        }.transformWhile { fetch ->
            emit(code to fetch)
            !(fetch is MatchFetch.Found && fetch.match.closesAt <= clock())
        }

    private fun duels(
        me: String?,
        inputs: Inputs,
        mine: List<Match>,
        fetched: Map<ShareCode, MatchFetch>,
        now: Instant,
    ): MyDuels {
        val takenBy = { seat: String -> inputs.outbox.filter { it.result.uid == seat }.groupBy { it.code } }
        val localByCode = inputs.locals.associateBy { it.code }
        val served = if (me == null) emptyList() else {
            val taken = takenBy(me)
            mine.mapNotNull { match -> rowOf(match, me, taken[match.code].orEmpty(), localByCode[match.code], now) }
        }
        val invited = if (me == null) emptyList() else {
            fetched.values.mapNotNull { (it as? MatchFetch.Found)?.match }.mapNotNull { match -> linkInvite(match, me, now) }
        }
        val takenSelf = takenBy(SelfSeats.ME)
        val selfRows = inputs.selfs.mapNotNull { match ->
            rowOf(match, SelfSeats.ME, takenSelf[match.code].orEmpty(), localByCode[match.code], now)
        }
        val (done, active) = (served + invited + selfRows).partition { it.kind is DuelRowKind.Finished }
        val seenAt = { row: DuelRow -> localByCode[row.code]?.revealSeenAt ?: Instant.EPOCH }
        val rows = active.sortedWith(
            compareBy<DuelRow> { rank(it.kind) }.thenBy { it.isSelf }.thenBy { it.deadline }.thenBy { it.code.value },
        ) + done.sortedWith(compareBy<DuelRow> { it.isSelf }.thenByDescending(seenAt).thenBy { it.code.value })
        return MyDuels(rows, rows.count { it.kind.needsYou() && !it.isSelf })
    }

    private fun rowOf(match: Match, me: String, taken: List<OutboxResult>, cached: LocalMatch?, now: Instant): DuelRow? {
        if (match.cancelled || match.declined) return null
        val seated = me == match.challenger.uid || me == match.opponent?.uid
        // Played here (any outbox row) or anywhere (my public progress): a puzzle played on my other device counts.
        val legsDone = taken.mapTo(HashSet()) { it.result.legIndex }.apply { addAll(match.progressOf(me)) }.size
        val kind = when {
            !seated -> if (match.invitedUid == me && match.opponent == null && now < match.deadline) DuelRowKind.Invited else null
            match.revealReady(me, taken, now) ->
                if (cached?.revealSeenAt == null) DuelRowKind.RevealReady else cached.finished(now)
            now >= match.closesAt -> null
            match.opponent == null -> DuelRowKind.Unclaimed
            legsDone < match.legs.size && now < match.deadline -> DuelRowKind.YourMove
            else -> DuelRowKind.Waiting
        } ?: return null
        val other = if (kind == DuelRowKind.Invited) match.challenger else match.opponentOf(me)
        val theirDone = if (kind == DuelRowKind.Invited || other == null) 0 else match.progressOf(other.uid).size
        return DuelRow(match.code, other, kind, legsDone, theirDone, match.legs.size, match.deadline, match.isSelf)
    }

    /** The match's puzzles [uid] has played, from the public progress; indices outside the match's legs do not count. */
    private fun Match.progressOf(uid: String): List<Int> = progress[uid].orEmpty().filter { it in legs.indices }

    /** An open, unclaimed match of someone else, known only from a cached link; the challenger is who invited me. */
    private fun linkInvite(match: Match, me: String, now: Instant): DuelRow? {
        val open = !match.cancelled && !match.declined && match.opponent == null && now < match.deadline
        if (!open || match.challenger.uid == me) return null
        return DuelRow(match.code, match.challenger, DuelRowKind.Invited, 0, 0, match.legs.size, match.deadline)
    }

    /** The finished row of a reveal seen here and still inside its stay at [now]; null once it is seen no longer or not seen. */
    private fun LocalMatch.finished(now: Instant): DuelRowKind.Finished? =
        revealSeenAt?.takeIf { now < it + FINISHED_KEPT }?.let { DuelRowKind.Finished(myScore, theirScore) }

    /** The earliest of each match's `closesAt` and `deadline`, and of each seen reveal's stay ending, still after [now]. */
    private fun nextBoundary(matches: List<Match>, locals: List<LocalMatch>, now: Instant): Instant? =
        (
            matches.flatMap { listOf(it.closesAt, it.deadline) } +
                locals.mapNotNull { it.revealSeenAt?.plus(FINISHED_KEPT) }
            ).filter { it > now }.minOrNull()

    private fun rank(kind: DuelRowKind): Int = when (kind) {
        DuelRowKind.Invited -> 0
        DuelRowKind.YourMove -> 1
        DuelRowKind.RevealReady -> 2
        DuelRowKind.Waiting, DuelRowKind.Unclaimed -> 3
        is DuelRowKind.Finished -> 4
    }

    private companion object {
        /** How long a duel stays in the list as a finished row after its reveal was first seen. */
        val FINISHED_KEPT: Duration = Duration.ofDays(7)
    }
}

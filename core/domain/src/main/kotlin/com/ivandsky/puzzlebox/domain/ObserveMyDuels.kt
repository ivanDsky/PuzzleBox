package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LocalMatch
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.coroutineScope

/**
 * The read model behind the Duels tab rows and the Play-tab badge. One `observeMine` listener, one `observe(code)`
 * listener per cached link invite that is still open, the local cache and my outbox; it never reads a sealed result
 * and shows no score or verdict (A7, A27). Rows needing me come first, then by the nearest deadline. The list
 * re-emits at each future time boundary (`closesAt`, and `deadline` while unclaimed) with no upstream change (A1).
 * Signed out, or an `observeMine` of null, is empty. Writes nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMyDuels(
    private val repo: MatchRepository,
    private val outbox: MatchResultOutbox,
    private val local: MatchLocalRepository,
    private val identity: IdentityService,
    private val clock: () -> Instant,
) {

    private class Inputs(val mine: List<Match>?, val locals: List<LocalMatch>, val outbox: List<OutboxResult>)

    operator fun invoke(): Flow<MyDuels> =
        identity.current.map { it?.uid }.distinctUntilChanged().flatMapLatest { me ->
            if (me == null) flowOf(EMPTY) else observeAs(me)
        }

    private fun observeAs(me: String): Flow<MyDuels> = flow {
        val inputs = MutableStateFlow<Inputs?>(null)
        coroutineScope {
            launch {
                combine(repo.observeMine(), local.observeAll(), outbox.observeAll(), ::Inputs).collect { inputs.value = it }
            }
            val latest = inputs.filterNotNull()
            val invites = latest.map { inviteCodes(it) }.distinctUntilChanged().flatMapLatest { codes ->
                if (codes.isEmpty()) flowOf(emptyMap()) else combine(codes.map { watched(it) }) { it.toMap() }
            }
            emitAll(
                combine(latest, invites) { now, fetched -> now to fetched }.transformLatest { (now, fetched) ->
                    val mine = now.mine
                    if (mine == null) {
                        emit(EMPTY)
                    } else {
                        var at = clock()
                        val candidates = mine + fetched.values.mapNotNull { (it as? MatchFetch.Found)?.match }
                        emit(duels(me, now, mine, fetched, at))
                        var next = nextBoundary(candidates, at)
                        while (next != null) {
                            delay(Duration.between(at, next).toKotlinDuration())
                            at = clock()
                            emit(duels(me, now, mine, fetched, at))
                            next = nextBoundary(candidates, at)
                        }
                    }
                }.distinctUntilChanged(),
            )
        }
    }

    /** Cached link invites of mine that no server row covers, so each needs its own fetch. */
    private fun inviteCodes(inputs: Inputs): Set<ShareCode> {
        val mine = inputs.mine ?: return emptySet()
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
        me: String,
        inputs: Inputs,
        mine: List<Match>,
        fetched: Map<ShareCode, MatchFetch>,
        now: Instant,
    ): MyDuels {
        val taken = inputs.outbox.filter { it.result.uid == me }.groupBy { it.code }
        val localByCode = inputs.locals.associateBy { it.code }
        val served = mine.mapNotNull { match ->
            rowOf(match, me, taken[match.code].orEmpty(), localByCode[match.code], now)
        }
        val invited = fetched.values.mapNotNull { (it as? MatchFetch.Found)?.match }
            .mapNotNull { match -> linkInvite(match, me, now) }
        val rows = (served + invited).sortedWith(
            compareBy<DuelRow> { rank(it.kind) }.thenBy { it.deadline }.thenBy { it.code.value },
        )
        return MyDuels(rows, rows.count { it.kind.needsYou() })
    }

    private fun rowOf(match: Match, me: String, taken: List<OutboxResult>, cached: LocalMatch?, now: Instant): DuelRow? {
        if (match.cancelled || match.declined) return null
        val seated = me == match.challenger.uid || me == match.opponent?.uid
        val kind = when {
            !seated -> if (match.invitedUid == me && match.opponent == null && now < match.closesAt) DuelRowKind.Invited else null
            match.revealReady(me, taken, now) -> DuelRowKind.RevealReady.takeIf { cached?.revealSeenAt == null }
            now >= match.closesAt -> null
            taken.size < match.legs.size -> DuelRowKind.YourMove
            match.opponent == null -> DuelRowKind.Unclaimed
            else -> DuelRowKind.Waiting
        } ?: return null
        val other = if (kind == DuelRowKind.Invited) match.challenger else match.opponentOf(me)
        return DuelRow(match.code, other, kind, taken.size, match.legs.size, match.deadline)
    }

    /** An open, unclaimed match of someone else, known only from a cached link; the challenger is who invited me. */
    private fun linkInvite(match: Match, me: String, now: Instant): DuelRow? {
        val open = !match.cancelled && !match.declined && match.opponent == null && now < match.closesAt
        if (!open || match.challenger.uid == me) return null
        return DuelRow(match.code, match.challenger, DuelRowKind.Invited, 0, match.legs.size, match.deadline)
    }

    /** The earliest of each match's `closesAt` and, while unclaimed, `deadline` that is still after [now]. */
    private fun nextBoundary(matches: List<Match>, now: Instant): Instant? =
        matches.flatMap { listOfNotNull(it.closesAt, it.deadline.takeIf { _ -> it.opponent == null }) }
            .filter { it > now }.minOrNull()

    private fun rank(kind: DuelRowKind): Int = when (kind) {
        DuelRowKind.Invited, DuelRowKind.YourMove, DuelRowKind.RevealReady -> kind.ordinal
        DuelRowKind.Waiting, DuelRowKind.Unclaimed -> DuelRowKind.Waiting.ordinal
    }

    private fun DuelRowKind.needsYou() =
        this == DuelRowKind.Invited || this == DuelRowKind.YourMove || this == DuelRowKind.RevealReady

    private companion object {
        val EMPTY = MyDuels(emptyList(), 0)
    }
}

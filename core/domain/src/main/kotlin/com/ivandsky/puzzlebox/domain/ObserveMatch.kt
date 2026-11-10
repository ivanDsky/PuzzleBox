package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.LocalMatch
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * The read model of one duel, for every match screen: the server match, my outbox rows, this device's cached row and
 * the signed-in identity, as one [MatchState]. The only caller of the sealed result read: an opponent's leg is read
 * once its progress names it and my own result for it is Posted, or the match has closed (a leg one player never
 * finished is lost to them, so the other's time must show), and a revealed result is kept for the collector's life.
 * A read that is Offline or Sealed after the close is tried again every [CLOSE_READ_RETRY]. Late and LocalOnly rows
 * are listed but never counted, nor is a Pending one once the match has closed. The view re-reads and re-emits at each
 * future time boundary (`closesAt`, and `deadline` while unclaimed) with no upstream change. Writes nothing and holds
 * no listener of its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMatch(
    private val repo: MatchRepository,
    private val outbox: MatchResultOutbox,
    private val local: MatchLocalRepository,
    private val identity: IdentityService,
    private val registry: GameRegistry,
    private val clock: () -> Instant,
) {

    operator fun invoke(code: ShareCode): Flow<MatchState> =
        identity.current.map { it?.uid }.distinctUntilChanged().flatMapLatest { me ->
            if (me == null) observePublic(code) else observeAs(code, me)
        }

    /** The match document alone: the rules let anyone `get` it, and it holds no results. Offline keeps the sign-in card bare. */
    private fun observePublic(code: ShareCode): Flow<MatchState> = repo.observe(code).map { fetch ->
        when (fetch) {
            MatchFetch.NotFound -> MatchState.NotFound
            MatchFetch.Offline -> MatchState.SignedOut(null)
            is MatchFetch.UnsupportedVersion -> MatchState.UpdateApp(fetch.found)
            is MatchFetch.Found -> MatchState.SignedOut(fetch.match)
        }
    }

    private fun observeAs(code: ShareCode, me: String): Flow<MatchState> {
        val revealed = HashMap<Int, LegResult>()
        return combine(repo.observe(code), outbox.observe(code), local.observeAll()) { fetch, rows, locals ->
            Triple(fetch, rows.filter { it.result.uid == me }, locals.firstOrNull { it.code == code })
        }.transformLatest { (fetch, mine, cached) ->
            when (fetch) {
                MatchFetch.NotFound -> emit(MatchState.NotFound)
                MatchFetch.Offline -> emit(MatchState.Offline)
                is MatchFetch.UnsupportedVersion -> emit(MatchState.UpdateApp(fetch.found))
                is MatchFetch.Found -> {
                    val match = fetch.match
                    var now = clock()
                    var seen = theirLegs(code, match, me, mine, revealed, now)
                    emit(MatchState.Loaded(view(match, me, mine, seen.legs, cached, now)))
                    var next = nextWake(match, now, seen.unread)
                    while (next != null) {
                        delay(Duration.between(now, next).toKotlinDuration())
                        now = clock()
                        seen = theirLegs(code, match, me, mine, revealed, now)
                        emit(MatchState.Loaded(view(match, me, mine, seen.legs, cached, now)))
                        next = nextWake(match, now, seen.unread)
                    }
                }
            }
        }
    }

    /** What [theirLegs] saw, and whether a read the rules allow came back Offline and so wants another try. */
    private class Seen(val legs: List<LegSeen>, val unread: Boolean)

    /**
     * One [LegSeen] per leg; reads a leg the opponent played once I posted mine or the match has closed (where a leg
     * I never finished is lost to me and must show their time), never before, and never one already revealed.
     */
    private suspend fun theirLegs(
        code: ShareCode,
        match: Match,
        me: String,
        mine: List<OutboxResult>,
        revealed: MutableMap<Int, LegResult>,
        now: Instant,
    ): Seen {
        val them = match.opponentOf(me)?.uid ?: return Seen(match.legs.map { LegSeen.NotPlayed }, unread = false)
        val played = match.progress[them].orEmpty()
        val posted = mine.filter { it.state == ResultPostState.Posted }.mapTo(HashSet()) { it.result.legIndex }
        val closed = now >= match.closesAt
        var unread = false
        val legs = match.legs.indices.map { leg ->
            val known = revealed[leg]
            when {
                leg !in played -> LegSeen.NotPlayed
                known != null -> LegSeen.Revealed(known)
                leg !in posted && !closed -> LegSeen.Played
                else -> when (val read = repo.readResult(code, leg, them)) {
                    is ResultRead.Found -> LegSeen.Revealed(read.result).also { revealed[leg] = read.result }
                    ResultRead.Missing -> LegSeen.Played
                    // After the close a Sealed answer means this clock is ahead of the server's: ask again shortly.
                    ResultRead.Sealed, ResultRead.Offline -> LegSeen.Played.also { unread = unread || closed }
                }
            }
        }
        return Seen(legs, unread)
    }

    private fun view(
        match: Match,
        me: String,
        mine: List<OutboxResult>,
        theirs: List<LegSeen>,
        cached: LocalMatch?,
        now: Instant,
    ): MatchView {
        // A Pending post is refused once the match has closed, so the opponent's device does not count it either.
        val closed = now >= match.closesAt
        val counted = mine.filter { it.state == ResultPostState.Posted || (it.state == ResultPostState.Pending && !closed) }
            .map { it.result } + theirs.filterIsInstance<LegSeen.Revealed>().map { it.result }
        val outcome = ComputeMatchOutcome(match, counted, me, now, registry::rankOrderOf)
        return MatchView(match, me, mine, theirs, outcome, cached)
    }

    /**
     * When to look again: the earliest of `closesAt` and, while unclaimed, `deadline` still after [now]; once both have
     * passed, [CLOSE_READ_RETRY] on while a read is [unread], else null.
     */
    private fun nextWake(match: Match, now: Instant, unread: Boolean): Instant? =
        listOfNotNull(match.closesAt, match.deadline.takeIf { match.opponent == null }).filter { it > now }.minOrNull()
            ?: now.plus(CLOSE_READ_RETRY).takeIf { unread }

    private companion object {
        /** How long after the close an Offline read of their result waits before the next try. */
        val CLOSE_READ_RETRY: Duration = Duration.ofSeconds(10)
    }
}

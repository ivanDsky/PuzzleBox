package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.guardedAsk
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Answers the one question the friends card needs, [FriendsStanding], and is the only recap code that reaches for the
 * network. [ObserveRecap] runs it after the local story is out, so nothing here can delay or fail a local card.
 *
 * - A year is [FriendsStanding.NotApplicable]: its branch reads the kept weekly rank history and arrives with the
 *   yearly recap (#1611).
 * - Signed out, or with no friends: [FriendsStanding.NotApplicable] (A12). Friends are [friendUids], the duel partners
 *   the Ranks hub's Friends scope is built from; no board is read to find out.
 * - On days 1–3 of the month after the period's, the month just ended is still taking late results, so the answer is
 *   [FriendsStanding.Settling] with the 4th, and no board is read (A21). It matches the rule that makes a board final
 *   three UTC days after its puzzle day.
 * - Otherwise each game the viewer solved in the period is read through [friendsOverall] over the period's
 *   [RecapPeriod.rankWindow], one read per game, together. The best game is the one with the most points for the viewer;
 *   a tie goes to the game listed first. A game the viewer has no row on adds nothing.
 * - A read that is only partial or offline cannot name a best game, so any such read, a failure, or the whole answer
 *   taking longer than [timeout] is [FriendsStanding.Unavailable] (A16), never a number that a later read could change.
 */
internal class ResolveFriendsStanding(
    private val identity: IdentityService,
    private val friendUids: () -> Flow<Set<String>>,
    private val friendsOverall: (RankWindow, List<GameId>) -> Flow<OverallState>,
    private val puzzleDay: GetPuzzleDay,
    private val failures: FailureLog = LogcatFailureLog,
    private val timeout: Duration = READ_TIMEOUT,
) {

    /** The friends answer for [period], reading the boards of the [played] games: those the viewer solved in it, in the app's game order. */
    suspend operator fun invoke(period: RecapPeriod, played: List<GameId>): FriendsStanding {
        val window = period.rankWindow ?: return FriendsStanding.NotApplicable
        val viewer = identity.current.value?.uid
        if (viewer == null || played.isEmpty()) return FriendsStanding.NotApplicable
        return guardedAsk(failures, TAG, "friends standing", timeout) { answer(period, window, viewer, played) }
            ?: FriendsStanding.Unavailable
    }

    private suspend fun answer(period: RecapPeriod, window: RankWindow, viewer: String, played: List<GameId>): FriendsStanding {
        if (friendUids().first().isEmpty()) return FriendsStanding.NotApplicable
        settling(period)?.let { return it }
        val reads = coroutineScope { played.map { game -> async { game to settled(window, game) } }.awaitAll() }
        val states = reads.map { it.second }
        return when {
            OverallState.SignedOut in states -> FriendsStanding.NotApplicable
            states.any { it !is OverallState.Complete && it != OverallState.Empty } -> FriendsStanding.Unavailable
            else -> best(reads, viewer)
        }
    }

    /** The late-results answer while [period] is the month just ended and it is still before the settle day, else null. */
    private fun settling(period: RecapPeriod): FriendsStanding.Settling? {
        val month = (period as? RecapPeriod.Month)?.month ?: return null
        val today = puzzleDay()
        val justEnded = YearMonth.from(today).minusMonths(1)
        return FriendsStanding.Settling(month.plusMonths(1).atDay(SETTLE_DAY)).takeIf { month == justEnded && today.dayOfMonth < SETTLE_DAY }
    }

    /** [game]'s read once it has answered for good: no longer loading, and not a half answer that more boards could change. */
    private suspend fun settled(window: RankWindow, game: GameId): OverallState =
        friendsOverall(window, listOf(game)).first { it != OverallState.Loading && it !is OverallState.Partial }

    private fun best(reads: List<Pair<GameId, OverallState>>, viewer: String): FriendsStanding {
        val mine = reads.mapNotNull { (game, state) ->
            val row = (state as? OverallState.Complete)?.standings?.rows?.firstOrNull { it.uid == viewer && it.games > 0 }
            row?.let { game to it.total }
        }
        val (game, points) = mine.maxByOrNull { it.second } ?: return FriendsStanding.NotApplicable
        return FriendsStanding.Loaded(game, points)
    }

    private companion object {
        const val TAG = "ResolveFriendsStanding"

        /** The day of the next month friends' results settle on (A21): the first day every board of the month is final. */
        const val SETTLE_DAY = 4

        /** How long the whole friends answer may take before the card says it cannot load. */
        val READ_TIMEOUT = 15.seconds
    }
}

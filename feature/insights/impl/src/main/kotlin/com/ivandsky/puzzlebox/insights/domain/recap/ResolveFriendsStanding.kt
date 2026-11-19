package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.guardedAsk
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
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
 * network. It works in two steps, so the card's place in the story is known before any read: [start] decides from what
 * the phone knows whether the card applies and holds its place, and [invoke] settles it. [ObserveRecap] tells the local
 * story with [start]'s answer and runs [invoke] after, so nothing here can delay or fail a local card.
 *
 * Who the friends are is not asked here. Friends Overall ([friendsOverall]) counts the duel partners and the LinkedIn
 * connections of the period's days (F21) through its own friend boards, and that source needs the boards the read
 * fetches; a second reading of it would be a second definition of a friend. So a signed-in viewer's card holds its
 * place as [FriendsStanding.Reading] until the read answers, and is dropped then if no friend turns up on its boards.
 *
 * - A year is [FriendsStanding.NotApplicable]: its branch reads the kept weekly rank history and arrives with the
 *   yearly recap (#1611).
 * - Signed out, or nothing played in the period: [FriendsStanding.NotApplicable] (A12), known locally.
 * - On days 1–3 of the month after the period's, the month just ended is still taking late results, so [start] is
 *   [FriendsStanding.Settling] with the 4th straight away, with no wait (A21). It matches the rule that makes a board
 *   final three UTC days after its puzzle day. The card needs no number to say that, but it still needs a friend to say
 *   it to: the same read runs after, and a read that shows nobody but the viewer drops the card. A read that fails,
 *   goes offline or times out leaves Settling as it is.
 * - Otherwise [FriendsStanding.Reading], and each game the viewer solved in the period is read through [friendsOverall]
 *   over the period's [RecapPeriod.rankWindow], one read per game, together. If no board shows anyone but the viewer
 *   the answer is [FriendsStanding.NotApplicable]. Else the best game is the one with the most points for the viewer; a
 *   tie goes to the game listed first. A game the viewer has no row on adds nothing.
 * - A read that is only partial or offline cannot name a best game, so any such read, a failure, or the whole answer
 *   taking longer than [timeout] is [FriendsStanding.Unavailable] (A16), never a number that a later read could change.
 */
internal class ResolveFriendsStanding(
    private val identity: IdentityService,
    private val friendsOverall: (RankWindow, List<GameId>) -> Flow<OverallState>,
    private val puzzleDay: GetPuzzleDay,
    private val failures: FailureLog = LogcatFailureLog,
    private val timeout: Duration = READ_TIMEOUT,
) {

    /**
     * The state the story opens with for [period], from what the phone knows: [FriendsStanding.Reading] when [invoke]
     * has a read to make, [FriendsStanding.Settling] when the month is settling (the read then only checks for friends), or
     * [FriendsStanding.NotApplicable] when the card does not apply.
     */
    fun start(period: RecapPeriod, played: List<GameId>): FriendsStanding {
        if (period.rankWindow == null || identity.current.value == null || played.isEmpty()) return FriendsStanding.NotApplicable
        return settling(period) ?: FriendsStanding.Reading
    }

    /** The settled state for [period], reading the boards of the [played] games: those the viewer solved in it, in the app's game order. */
    suspend operator fun invoke(period: RecapPeriod, played: List<GameId>): FriendsStanding {
        val begun = start(period, played)
        if (begun == FriendsStanding.NotApplicable) return begun
        val window = period.rankWindow ?: return FriendsStanding.NotApplicable
        val viewer = identity.current.value?.uid ?: return FriendsStanding.NotApplicable
        val settling = begun as? FriendsStanding.Settling
        return guardedAsk(failures, TAG, "friends standing", timeout) { answer(window, viewer, played, settling) }
            ?: settling
            ?: FriendsStanding.Unavailable
    }

    /** The answer from the reads; a [settling] month keeps its state unless the reads find no friends, as no number is needed. */
    private suspend fun answer(window: RankWindow, viewer: String, played: List<GameId>, settling: FriendsStanding.Settling?): FriendsStanding {
        val reads = coroutineScope { played.map { game -> async { game to settled(window, game) } }.awaitAll() }
        val states = reads.map { it.second }
        return when {
            OverallState.SignedOut in states -> FriendsStanding.NotApplicable
            states.any { it !is OverallState.Complete && it != OverallState.Empty } -> settling ?: FriendsStanding.Unavailable
            states.none { state -> state.rows().any { it.uid != viewer } } -> FriendsStanding.NotApplicable
            else -> settling ?: best(reads, viewer)
        }
    }

    /** The late-results state while [period] is the month just ended and it is still before the settle day, else null. */
    private fun settling(period: RecapPeriod): FriendsStanding.Settling? {
        val month = (period as? RecapPeriod.Month)?.month ?: return null
        val today = puzzleDay()
        val justEnded = YearMonth.from(today).minusMonths(1)
        return FriendsStanding.Settling(month.plusMonths(1).atDay(SETTLE_DAY)).takeIf { month == justEnded && today.dayOfMonth < SETTLE_DAY }
    }

    /** [game]'s read once it has answered for good: no longer loading, and not a half answer that more boards could change. */
    private suspend fun settled(window: RankWindow, game: GameId): OverallState =
        friendsOverall(window, listOf(game)).first { it != OverallState.Loading && it !is OverallState.Partial }

    private fun OverallState.rows(): List<OverallStandings.Row> = (this as? OverallState.Complete)?.standings?.rows.orEmpty()

    private fun best(reads: List<Pair<GameId, OverallState>>, viewer: String): FriendsStanding {
        val mine = reads.mapNotNull { (game, state) ->
            val row = state.rows().firstOrNull { it.uid == viewer && it.games > 0 }
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

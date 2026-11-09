package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.MatchVerdict
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The summary's pure model, with fixed inputs; Ivan is the viewer, Sam the opponent. */
class RevealSummaryModelTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val zip = GameId("zip")
    private val queens = GameId("queens")
    private val strands = GameId("strands")

    private fun leg(
        mine: DuelSideState,
        theirs: DuelSideState,
        verdict: LegVerdict,
        game: GameId = zip,
    ) = LegReveal(game, mine, theirs, verdict)

    private fun time(ms: Long, hints: Int = 0) = DuelSideState.Time(ms, hints)

    @Test
    fun verdictKindFollowsTheMatchVerdict() {
        assertEquals(VerdictKind.Win, verdictKind(MatchVerdict.Won, myWins = 1, theirWins = 2))
        assertEquals(VerdictKind.Loss, verdictKind(MatchVerdict.Lost, myWins = 2, theirWins = 1))
        assertEquals(VerdictKind.Draw, verdictKind(MatchVerdict.Drawn, myWins = 1, theirWins = 0))

        assertEquals("You win", pillText(VerdictKind.Win, "Sam"))
        assertEquals("Sam wins", pillText(VerdictKind.Loss, "Sam"))
        assertEquals("Draw", pillText(VerdictKind.Draw, "Sam"))

        assertEquals("You take it 2–1", verdictText(VerdictKind.Win, "Sam", 2, 1))
        assertEquals("Sam takes it 1–2", verdictText(VerdictKind.Loss, "Sam", 1, 2))
        assertEquals("Dead even 1–1", verdictText(VerdictKind.Draw, "Sam", 1, 1))
    }

    @Test
    fun anUnsettledMatchFallsBackToTheScoresItHas() {
        assertEquals(VerdictKind.Win, verdictKind(null, myWins = 2, theirWins = 1))
        assertEquals(VerdictKind.Loss, verdictKind(null, myWins = 0, theirWins = 1))
        assertEquals(VerdictKind.Draw, verdictKind(null, myWins = 1, theirWins = 1))
    }

    @Test
    fun puzzlesWonCountsMyWinsFastestIsMyBestTimeAndTotalIsMyTimes() {
        val legs = listOf(
            leg(time(58_000), time(72_000), LegVerdict.Win(LegBasis.Time)),
            leg(time(95_999), time(80_000), LegVerdict.Loss(LegBasis.Time)),
            leg(DuelSideState.GaveUp, time(60_000), LegVerdict.Loss(LegBasis.Time)),
            leg(DuelSideState.NotPlayed, DuelSideState.NotPlayed, LegVerdict.Void),
            leg(time(61_000), time(61_400), LegVerdict.Draw),
        )

        val tiles = summaryTiles(legs)

        assertEquals(1, tiles.puzzlesWon)
        assertEquals(5, tiles.puzzles)
        assertEquals(58_000L, tiles.fastestMs)
        assertEquals(58_000L + 95_999L + 61_000L, tiles.totalMs)
    }

    @Test
    fun withNoTimeOfMineThereIsNoFastestAndTheTotalIsZero() {
        val tiles = summaryTiles(listOf(leg(DuelSideState.GaveUp, time(60_000), LegVerdict.Loss(LegBasis.Time))))

        assertNull(tiles.fastestMs)
        assertEquals(0L, tiles.totalMs)
        assertEquals(0, tiles.puzzlesWon)
    }

    @Test
    fun shareTextHasScoreAndOneSquarePerPuzzleThenTheMatchLink() {
        val verdicts = listOf(
            LegVerdict.Win(LegBasis.Time),
            LegVerdict.Loss(LegBasis.Hints),
            LegVerdict.Draw,
            LegVerdict.Void,
            LegVerdict.Win(LegBasis.Time),
        )

        val text = shareText("Ivan", "Sam", myWins = 2, theirWins = 1, verdicts = verdicts, link = code.matchLink)

        assertEquals("PuzzleBox · Ivan 2–1 Sam 🟩🟥⬜⬜🟩 · ${code.matchLink}", text)
        assertTrue(text.contains("/m/"))
    }

    @Test
    fun rematchSettingsKeepCountGamesDeadlineAndFriendsAndDrawFresh() {
        val match = match(refs = listOf(zip, queens, zip), deadlineHours = 48, includeFriends = true)

        val settings = rematchSettings(match)

        assertEquals(3, settings.count)
        assertEquals(setOf(zip, queens), settings.games)
        assertEquals(48, settings.deadlineHours)
        assertTrue(settings.includeFriends)
        assertFalse(settings.todayOnly)
        assertTrue(settings.pastedCodes.isEmpty())
        assertFalse(rematchSettings(match(refs = listOf(strands), deadlineHours = 24, includeFriends = false)).includeFriends)
    }

    @Test
    fun puzzleRowsNameTheWinnerShowEachTimeAndMarkAMissingResult() {
        val cards = listOf(
            card(leg(time(58_000), time(72_000), LegVerdict.Win(LegBasis.Time)), "Zip #401"),
            card(leg(time(95_000), time(80_000), LegVerdict.Loss(LegBasis.Time)), "Queens #88"),
            card(leg(time(61_000), time(61_400), LegVerdict.Draw), "Zip #402"),
            card(leg(DuelSideState.NotPlayed, time(60_000), LegVerdict.Loss(LegBasis.Time)), "Queens #89"),
        )

        val rows = puzzleRows(cards)

        assertEquals(Side.Mine, rows[0].winner)
        assertEquals("0:58", rows[0].mine)
        assertEquals("1:12", rows[0].theirs)
        assertEquals(Side.Theirs, rows[1].winner)
        assertNull(rows[2].winner)
        assertTrue(rows[2].tied)
        assertFalse(rows[0].tied)
        assertEquals("No result", rows[3].mine)
        assertEquals("1:00", rows[3].theirs)
    }

    @Test
    fun theSummaryCardNamesTheDayTheDuelWasCreatedNotItsDeadline() {
        val match = match(refs = listOf(zip), deadlineHours = 48, includeFriends = false)
        val plan = RevealPlan(match.challenger, match.opponent!!, listOf(card(leg(time(1_000), time(2_000), LegVerdict.Win(LegBasis.Time)), "Zip #1")))
        val outcome = Outcome(plan.legs.map { it.verdict }, 1, 0, true, MatchVerdict.Won)

        val summary = duelSummary(match, plan, outcome, java.time.ZoneOffset.UTC, java.util.Locale.US)

        assertEquals("Best of 1 · Mon 9 Nov", summary.bestOf)
    }

    private fun card(leg: LegReveal, title: String) =
        RevealCard(leg, title, com.ivandsky.puzzlebox.game.RankOrder.TimeThenHints, basis = "", pill = "")

    private fun match(refs: List<GameId>, deadlineHours: Int, includeFriends: Boolean) = Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = Instant.parse("2026-11-09T10:00:00Z"),
        deadlineHours = deadlineHours,
        challenger = Player("ivan", "Ivan", null),
        invitedUid = null,
        opponent = Player("sam", "Sam", null),
        legs = refs.mapIndexed { i, id -> MatchLeg(LegRef.Daily(id, LocalDate.of(2026, 11, 9), 400 + i), authorUid = null) },
        includeFriends = includeFriends,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = emptyMap(),
    )
}

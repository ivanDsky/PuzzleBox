package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.material3.Text
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The mapping (rank, self, 1:1 states, note only on Loaded) and each state's rendering. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DailyBoardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val me = Identity(uid = "me", displayName = "Me", photoUrl = null)

    private fun entry(uid: String, name: String, timeMs: Long, hints: Int) = LeaderboardEntry(
        uid = uid, displayName = name, puzzleRef = "queens/2026-11-08/1", timeMs = timeMs, hints = hints, completedAtMs = 0L,
    )

    private fun loaded(vararg entries: LeaderboardEntry) = TodayLeaderboard.SignedIn(
        me,
        TodayLeaderboard.Board.Loaded(LeaderboardSnapshot(LeaderboardRef("queens", LocalDate.of(2026, 11, 8)), entries.toList())),
    )

    private fun show(order: RankOrder, state: DailyBoardState, ghosts: List<GhostRowModel> = emptyList()) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                DailyBoard("Queens · today", order, state, ghosts = ghosts, signIn = { Text("sign-in slot") })
            }
        }
    }

    private fun ghost(name: String, seconds: Int?, hintFree: Boolean = true) =
        GhostRowModel("ref-$name", name, seconds?.let { it * 1000L }, hintFree)

    private fun real(rank: Int, seconds: Int, isSelf: Boolean = false) =
        DailyBoardRowModel(rank, "r$seconds", seconds * 1000L, 0, isSelf, playerId = "r$seconds")

    private val anyRow = hasTestTag("daily_board.row") or hasTestTag("daily_board.ghost")

    @Test
    fun `rank is position plus one and self is by uid`() {
        val state = loaded(entry("a", "Ann", 60_000, 0), entry("me", "Me", 70_000, 1), entry("b", "Bo", 80_000, 0)).toDailyBoardState()
        assertEquals(
            DailyBoardState.Ranked(
                listOf(
                    DailyBoardRowModel(1, "Ann", 60_000, 0, false, playerId = "a"),
                    DailyBoardRowModel(2, "Me", 70_000, 1, true, playerId = "me"),
                    DailyBoardRowModel(3, "Bo", 80_000, 0, false, playerId = "b"),
                ),
            ),
            state,
        )
    }

    @Test
    fun `non-loaded states map one to one and drop the note`() {
        assertEquals(DailyBoardState.SignedOut, TodayLeaderboard.SignedOut.toDailyBoardState(DailyBoardNote.Posting))
        assertEquals(DailyBoardState.Loading, TodayLeaderboard.SignedIn(me, TodayLeaderboard.Board.Loading).toDailyBoardState(DailyBoardNote.Posting))
        assertEquals(DailyBoardState.Unavailable, TodayLeaderboard.SignedIn(me, TodayLeaderboard.Board.Unavailable).toDailyBoardState(DailyBoardNote.Posting))
    }

    @Test
    fun `the note rides only on a loaded board`() {
        val state = loaded().toDailyBoardState(DailyBoardNote.ReplayFirstTimeStands)
        assertEquals(DailyBoardState.Ranked(emptyList(), DailyBoardNote.ReplayFirstTimeStands), state)
    }

    @Test
    fun `ranked shows title caption rows and tags the self row`() {
        val rows = listOf(DailyBoardRowModel(1, "Ann", 60_000, 0, false, playerId = "ann"), DailyBoardRowModel(2, "Me", 70_000, 0, true, playerId = "me"))
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(rows))

        composeRule.onNodeWithTag("daily_board.title").assertTextEquals("Queens · today")
        composeRule.onNodeWithTag("daily_board.caption").assertTextEquals("Fastest first, then fewer hints")
        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(1)
        composeRule.onAllNodesWithTag("daily_board.row.self").assertCountEquals(1)
        composeRule.onAllNodesWithTag("daily_board.note").assertCountEquals(0)
    }

    @Test
    fun `a hints-first row leads with hints`() {
        show(RankOrder.HintsThenTime, DailyBoardState.Ranked(listOf(DailyBoardRowModel(1, "Ann", 252_000, 2, false, playerId = "ann"))))

        composeRule.onNodeWithTag("daily_board.caption").assertTextEquals("Fewest hints first, then time")
        composeRule.onNodeWithTag("daily_board.row.score").assertTextEquals("2 hints · 4:12")
    }

    @Test
    fun `a time-first row leads with time`() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(listOf(DailyBoardRowModel(1, "Ann", 252_000, 2, false, playerId = "ann"))))

        composeRule.onNodeWithTag("daily_board.row.score").assertTextEquals("4:12")
    }

    @Test
    fun `empty ranked says nobody is on the board`() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList()))
        composeRule.onNodeWithText("No one is on today's board yet.").assertIsDisplayed()
    }

    @Test
    fun `title is game name plus today, Today when the name is unknown`() {
        assertEquals("Queens · today", dailyBoardTitle("Queens"))
        assertEquals("Strands · today", dailyBoardTitle("Strands"))
        assertEquals("Today", dailyBoardTitle(null))
    }

    @Test
    fun `notes read their copy`() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList(), DailyBoardNote.Posting))
        composeRule.onNodeWithTag("daily_board.note").assertTextEquals("Posting your time…")
    }

    @Test
    fun `replay note reads its copy`() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList(), DailyBoardNote.ReplayFirstTimeStands))
        composeRule.onNodeWithTag("daily_board.note").assertTextEquals("Replay — your first time stands")
    }

    @Test
    fun `a deferred post reads its copy`() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList(), DailyBoardNote.Deferred))
        composeRule.onNodeWithTag("daily_board.note").assertTextEquals("Saved on this device — it'll post when you're online.")
    }

    @Test
    fun `a rejected post reads its copy`() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList(), DailyBoardNote.Rejected))
        composeRule.onNodeWithTag("daily_board.note").assertTextEquals("Couldn't post this time.")
    }

    @Test
    fun `a posted time outside the rows reads its copy`() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList(), DailyBoardNote.PostedOutsideTop))
        composeRule.onNodeWithTag("daily_board.note").assertTextEquals("Your time is posted — outside the top 100.")
    }

    @Test
    fun `each post status maps to its note, a posted one to outside the rows`() {
        assertEquals(DailyBoardNote.Posting, SubmitStatus.Posting.toBoardNote())
        assertEquals(DailyBoardNote.Deferred, SubmitStatus.Deferred.toBoardNote())
        assertEquals(DailyBoardNote.Rejected, SubmitStatus.Rejected.toBoardNote())
        assertEquals(DailyBoardNote.PostedOutsideTop, SubmitStatus.Posted.toBoardNote())
    }

    @Test
    fun `loading and unavailable show their line and no rows`() {
        show(RankOrder.TimeThenHints, DailyBoardState.Loading)
        composeRule.onNodeWithTag("daily_board.loading").assertIsDisplayed()
        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(0)
    }

    @Test
    fun `unavailable shows its soft line`() {
        show(RankOrder.TimeThenHints, DailyBoardState.Unavailable)
        composeRule.onNodeWithTag("daily_board.unavailable").assertIsDisplayed()
    }

    @Test
    fun `signed out renders the callers sign-in slot`() {
        show(RankOrder.TimeThenHints, DailyBoardState.SignedOut)
        composeRule.onNodeWithText("sign-in slot").assertIsDisplayed()
    }

    @Test
    fun ghostsAreDrawnBetweenTheRealRowsInRankOrder() {
        val rows = listOf(real(1, 60), real(2, 100))
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(rows), listOf(ghost("Ada", 80)))

        val lines = composeRule.onAllNodes(anyRow)
        lines.assertCountEquals(3)
        lines[0].assert(hasTestTag("daily_board.row"))
        lines[1].assert(hasTestTag("daily_board.ghost"))
        lines[2].assert(hasTestTag("daily_board.row"))
        composeRule.onAllNodesWithText("—").assertCountEquals(0)
        composeRule.onNodeWithTag("daily_board.ghost.rank").assertTextEquals("2")
        val ranks = composeRule.onAllNodesWithTag("daily_board.row.rank")
        ranks.assertCountEquals(2)
        ranks[0].assertTextEquals("1")
        ranks[1].assertTextEquals("3")
    }

    @Test
    fun aBoardWithGhostsTellsEachPuzzleBoxRankAndOneWithoutDoesNot() {
        val rows = listOf(real(1, 60), real(2, 100))
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(rows), listOf(ghost("Ada", 80)))

        val notes = composeRule.onAllNodesWithTag("daily_board.row.puzzlebox_rank")
        notes.assertCountEquals(2)
        notes[0].assertTextEquals("#1 on PuzzleBox")
        notes[1].assertTextEquals("#2 on PuzzleBox")
    }

    @Test
    fun aBoardWithoutGhostsDrawsTodaysRanksAndNoPuzzleBoxNote() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(listOf(real(1, 60), real(2, 100))))

        composeRule.onAllNodesWithTag("daily_board.row.puzzlebox_rank").assertCountEquals(0)
        composeRule.onAllNodesWithTag("daily_board.row.rank")[1].assertTextEquals("2")
    }

    @Test
    fun aHiddenGhostHasNoPlace() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(listOf(real(1, 60))), listOf(ghost("Hidden", null)))

        composeRule.onNodeWithTag("daily_board.ghost.rank").assertTextEquals("—")
    }

    @Test
    fun theCallersLinesAreDrawnAsGivenWithThePinnedPlace() {
        val top = real(1, 60)
        val me = real(7, 300, isSelf = true)
        val lines = listOf(DailyBoardLine.Real(top, 1), DailyBoardLine.Ghost(ghost("Ada", 80), 2))
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(listOf(top), pinned = me, lines = lines, pinnedPlace = 9))

        composeRule.onNodeWithTag("daily_board.ghost.rank").assertTextEquals("2")
        val ranks = composeRule.onAllNodesWithTag("daily_board.row.rank")
        ranks[ranks.fetchSemanticsNodes().size - 1].assertTextEquals("9")
        composeRule.onNodeWithTag("leaderboard.you").assertIsDisplayed()
        composeRule.onAllNodesWithTag("daily_board.row.puzzlebox_rank")[1].assertTextEquals("#7 on PuzzleBox")
    }

    @Test
    fun aGhostShowsItsOwnerAndTheLinkedInDotInsteadOfText() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList()), listOf(ghost("Ada", 80)))

        composeRule.onNodeWithTag("daily_board.ghost.name").assertTextEquals("Ada")
        composeRule.onNodeWithTag("daily_board.ghost.detail").assertDoesNotExist()
        composeRule.onNodeWithText("on this phone", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("daily_board.ghost.linkedin_dot").assertContentDescriptionEquals("LinkedIn")
    }

    @Test
    fun aGhostWithoutANameIsAConnection() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList()), listOf(ghost("", 80)))

        composeRule.onNodeWithTag("daily_board.ghost.name").assertTextEquals("Connection")
    }

    @Test
    fun aHiddenTimeSaysSoAndSortsLast() {
        val rows = listOf(real(1, 60), real(2, 100))
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(rows), listOf(ghost("Hidden", null)))

        composeRule.onNodeWithTag("daily_board.ghost.score").assertTextEquals("time hidden")
        val lines = composeRule.onAllNodes(anyRow)
        lines.assertCountEquals(3)
        lines[2].assert(hasTestTag("daily_board.ghost"))
    }

    @Test
    fun aHintFreeGhostReadsPerfectOnAHintsFirstBoard() {
        show(RankOrder.HintsThenTime, DailyBoardState.Ranked(emptyList()), listOf(ghost("Ada", 48)))
        composeRule.onNodeWithTag("daily_board.ghost.score").assertTextEquals("Perfect · 0:48")
    }

    @Test
    fun aGhostThatUsedHintsShowsTheTimeAlone() {
        show(RankOrder.HintsThenTime, DailyBoardState.Ranked(emptyList()), listOf(ghost("Ada", 48, hintFree = false)))
        composeRule.onNodeWithTag("daily_board.ghost.score").assertTextEquals("0:48")
    }

    @Test
    fun aGhostOnATimeFirstBoardShowsTheTime() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList()), listOf(ghost("Ada", 48)))
        composeRule.onNodeWithTag("daily_board.ghost.score").assertTextEquals("0:48")
    }

    @Test
    fun ghostsReplaceTheEmptyLine() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList()), listOf(ghost("Ada", 80)))
        composeRule.onAllNodesWithTag("daily_board.empty").assertCountEquals(0)
        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(1)
    }

    @Test
    fun withNoRowsAndNoGhostsTheEmptyLineShows() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList()))
        composeRule.onNodeWithTag("daily_board.empty").assertIsDisplayed()
    }

    @Test
    fun signedOutListsGhostsAboveTheSignInSlot() {
        show(RankOrder.TimeThenHints, DailyBoardState.SignedOut, listOf(ghost("Ada", 80), ghost("Bo", 90)))

        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(2)
        val slotTop = composeRule.onNodeWithText("sign-in slot").getBoundsInRoot().top
        val lastGhostBottom = composeRule.onAllNodesWithTag("daily_board.ghost")[1].getBoundsInRoot().bottom
        assertTrue(lastGhostBottom <= slotTop)
    }

    @Test
    fun signedOutWithoutGhostsStillShowsTheSlot() {
        show(RankOrder.TimeThenHints, DailyBoardState.SignedOut)
        composeRule.onNodeWithText("sign-in slot").assertIsDisplayed()
        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(0)
    }

    @Test
    fun loadingAndUnavailableDrawNoGhosts() {
        val ghosts = listOf(ghost("Ada", 80))
        show(RankOrder.TimeThenHints, DailyBoardState.Loading, ghosts)
        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(0)
    }

    @Test
    fun unavailableDrawsNoGhosts() {
        show(RankOrder.TimeThenHints, DailyBoardState.Unavailable, listOf(ghost("Ada", 80)))
        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(0)
        composeRule.onNodeWithTag("daily_board.unavailable").assertIsDisplayed()
    }

    @Test
    fun thePinnedRowStaysBelowTheDivider() {
        val state = DailyBoardState.Ranked(listOf(real(1, 60)), pinned = real(9, 300, isSelf = true))
        show(RankOrder.TimeThenHints, state, listOf(ghost("Ada", 80), ghost("Bo", 90)))

        val lines = composeRule.onAllNodes(anyRow or hasTestTag("leaderboard.you"))
        lines.assertCountEquals(4)
        lines[3].assert(hasTestTag("leaderboard.you"))
    }

    @Test
    fun onlyTheFirstTwentyGhostsAreDrawn() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList()), (1..30).map { ghost("g$it", it) })
        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(20)
    }

    @Test
    fun theCapDropsHiddenTimesBeforeTimedOnes() {
        val ghosts = (1..20).map { ghost("g$it", it) } + ghost("Hidden", null)
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList()), ghosts)

        composeRule.onAllNodesWithTag("daily_board.ghost").assertCountEquals(20)
        composeRule.onAllNodesWithText("time hidden").assertCountEquals(0)
    }
}

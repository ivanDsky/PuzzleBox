package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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

    private fun show(order: RankOrder, state: DailyBoardState) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                DailyBoard("Queens · today", order, state, signIn = { Text("sign-in slot") })
            }
        }
    }

    @Test
    fun `rank is position plus one and self is by uid`() {
        val state = loaded(entry("a", "Ann", 60_000, 0), entry("me", "Me", 70_000, 1), entry("b", "Bo", 80_000, 0)).toDailyBoardState()
        assertEquals(
            DailyBoardState.Ranked(
                listOf(
                    DailyBoardRowModel(1, "Ann", 60_000, 0, false),
                    DailyBoardRowModel(2, "Me", 70_000, 1, true),
                    DailyBoardRowModel(3, "Bo", 80_000, 0, false),
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
        val rows = listOf(DailyBoardRowModel(1, "Ann", 60_000, 0, false), DailyBoardRowModel(2, "Me", 70_000, 0, true))
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(rows))

        composeRule.onNodeWithTag("daily_board.title").assertTextEquals("Queens · today")
        composeRule.onNodeWithTag("daily_board.caption").assertTextEquals("Fastest first, then fewer hints")
        composeRule.onAllNodesWithTag("daily_board.row").assertCountEquals(1)
        composeRule.onAllNodesWithTag("daily_board.row.self").assertCountEquals(1)
        composeRule.onAllNodesWithTag("daily_board.note").assertCountEquals(0)
    }

    @Test
    fun `a hints-first row leads with hints`() {
        show(RankOrder.HintsThenTime, DailyBoardState.Ranked(listOf(DailyBoardRowModel(1, "Ann", 252_000, 2, false))))

        composeRule.onNodeWithTag("daily_board.caption").assertTextEquals("Fewest hints first, then time")
        composeRule.onNodeWithTag("daily_board.row.score").assertTextEquals("2 hints · 4:12")
    }

    @Test
    fun `a time-first row leads with time`() {
        show(RankOrder.TimeThenHints, DailyBoardState.Ranked(listOf(DailyBoardRowModel(1, "Ann", 252_000, 2, false))))

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
}

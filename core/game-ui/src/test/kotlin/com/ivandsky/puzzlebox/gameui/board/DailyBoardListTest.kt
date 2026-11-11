package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The one list every board draws: its lines with the board's gaps, and the player's own row pinned under them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DailyBoardListTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun real(rank: Int, place: Int = rank, self: Boolean = false) =
        DailyBoardLine.Real(DailyBoardRowModel(rank, "Player $rank", rank * 10_000L, 0, self, playerId = "player-$rank"), place)

    private fun ghost(place: Int?) = DailyBoardLine.Ghost(GhostRowModel("ref-$place", "Ada $place", 25_000L, hintFree = true), place)

    private fun show(lines: List<DailyBoardLine>, pinned: DailyBoardLine.Real? = null) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) { DailyBoardList(lines, RankOrder.TimeThenHints, pinned = pinned) }
        }
    }

    @Test
    fun `rows are set apart by the board's gap`() {
        show(listOf(real(1), ghost(2), real(3)))

        val rows = (composeRule.onAllNodesWithTag("daily_board.row").fetchSemanticsNodes().size +
            composeRule.onAllNodesWithTag("daily_board.ghost").fetchSemanticsNodes().size)
        assertEquals(3, rows)
        val first = composeRule.onAllNodesWithTag("daily_board.row")[0].getBoundsInRoot()
        val second = composeRule.onNodeWithTag("daily_board.ghost").getBoundsInRoot()
        val third = composeRule.onAllNodesWithTag("daily_board.row")[1].getBoundsInRoot()
        assertEquals(Spacing.sm, second.top - first.bottom)
        assertEquals(Spacing.sm, third.top - second.bottom)
    }

    @Test
    fun `the pinned own row sits under the lines, after a divider and the gaps`() {
        show(listOf(real(1), real(2)), pinned = real(7, self = true))

        composeRule.onAllNodesWithTag("daily_board.row.self").assertCountEquals(1)
        composeRule.onNodeWithTag("leaderboard.you").assertExists()
        val last = composeRule.onAllNodesWithTag("daily_board.row")[1].getBoundsInRoot()
        val pinned = composeRule.onNodeWithTag("leaderboard.you").getBoundsInRoot()
        assertTrue("pinned $pinned under the last line $last", pinned.top >= last.bottom + Spacing.sm)
    }

    @Test
    fun `no pinned row means no divider row and nothing under the lines`() {
        show(listOf(real(1), real(2)))

        composeRule.onAllNodesWithTag("leaderboard.you").assertCountEquals(0)
        composeRule.onAllNodesWithTag("daily_board.row.self").assertCountEquals(0)
    }

    @Test
    fun `a board whose places differ from PuzzleBox ranks names each PuzzleBox rank, a plain one does not`() {
        show(listOf(real(1), ghost(2), real(2, place = 3)), pinned = real(9, place = 4, self = true))

        composeRule.onAllNodesWithTag("daily_board.row.puzzlebox_rank").assertCountEquals(3)
    }

    @Test
    fun `a plain board names no PuzzleBox rank`() {
        show(listOf(real(1), real(2)), pinned = real(7, self = true))

        composeRule.onAllNodesWithTag("daily_board.row.puzzlebox_rank").assertCountEquals(0)
    }

    @Test
    fun `the reserved height holds five rows and a pinned one`() {
        show((1..5).map { real(it) } , pinned = real(9, self = true))

        val list = composeRule.onNodeWithTag("daily_board.list").getBoundsInRoot()
        assertTrue("list ${list.height} within the reserved ${dailyBoardListHeight(5, pinned = true)}", list.height <= dailyBoardListHeight(5, pinned = true))
    }
}

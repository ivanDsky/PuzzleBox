package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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

    private fun show(lines: List<DailyBoardLine>, pinned: DailyBoardLine.Real? = null, medals: Boolean = false) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) { DailyBoardList(lines, RankOrder.TimeThenHints, pinned = pinned, medals = medals) }
        }
    }

    private fun row(vararg lines: DailyBoardLine) = lines.toList()

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

    @Test
    fun `a row beyond the page reads 100+ in place of a number`() {
        val beyond = DailyBoardRowModel(120, "You", 300_000L, 2, true, playerId = "you", beyondPage = true)
        show(row(real(1)), pinned = DailyBoardLine.Real(beyond, 120))

        composeRule.onAllNodesWithTag("daily_board.row.rank")[0].assertTextEquals("1")
        composeRule.onAllNodesWithTag("daily_board.row.rank")[1].assertTextEquals("100+")
    }

    @Test
    fun `an unofficial row keeps its place and carries the mark, an official one has none`() {
        val unofficial = DailyBoardRowModel(2, "You", 15_000L, 0, true, playerId = "you", unofficial = true)
        show(row(real(1), DailyBoardLine.Real(unofficial, 2)))

        composeRule.onAllNodesWithTag("daily_board.row.mark").assertCountEquals(1)
        composeRule.onNodeWithTag("daily_board.row.mark").assertTextEquals("unofficial")
        composeRule.onAllNodesWithTag("daily_board.row.rank")[1].assertTextEquals("2")
    }

    @Test
    fun `a row with a points value says its points and games`() {
        val points = DailyBoardRowModel(1, "Maya", 0L, 0, false, playerId = "maya", value = BoardRowValue.Points(1240.0, games = 5, atLeast = true))
        show(row(DailyBoardLine.Real(points, 1)))

        composeRule.onNodeWithTag("daily_board.row.score").assertTextEquals("≥1,240 pts")
        composeRule.onNodeWithTag("daily_board.row.puzzlebox_rank").assertTextEquals("5 games")
    }

    @Test
    fun `medal rows are only with medals on, and the winner's is taller`() {
        show(row(real(1), real(2), real(3), real(4)), medals = true)

        val heights = composeRule.onAllNodesWithTag("daily_board.row").fetchSemanticsNodes().map { it.size.height }
        assertTrue("the winner's row $heights is the tallest", heights[0] > heights[1] && heights[1] == heights[2] && heights[2] == heights[3])
        val winner = composeRule.onAllNodesWithTag("daily_board.row")[0].getBoundsInRoot().height
        assertEquals(72.dp, winner)
        assertEquals(BoardRowMinHeight, composeRule.onAllNodesWithTag("daily_board.row")[1].getBoundsInRoot().height)
    }

    @Test
    fun `without medals the winner's row is as tall as any`() {
        show(row(real(1), real(2)))

        assertEquals(BoardRowMinHeight, composeRule.onAllNodesWithTag("daily_board.row")[0].getBoundsInRoot().height)
    }

    @Test
    fun `the tint steps down from gold to silver to bronze and the rest have none`() {
        assertEquals(0.18f, medalRowOf(1)!!.tint.alpha, 0.01f)
        assertEquals(0.11f, medalRowOf(2)!!.tint.alpha, 0.01f)
        assertEquals(0.06f, medalRowOf(3)!!.tint.alpha, 0.01f)
        assertEquals(null, medalRowOf(4))
        assertEquals(null, medalRowOf(null))
        assertEquals(72.dp, medalRowOf(1)!!.minHeight)
        assertEquals(BoardRowMinHeight, medalRowOf(3)!!.minHeight)
    }
}

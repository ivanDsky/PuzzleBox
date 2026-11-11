package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A PuzzleBox row and a connection row are one design: the same parts, in the same order, in the same frame. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoardRowLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val real = DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya")
    private val self = DailyBoardRowModel(2, "Jonas", 75_000L, 1, isSelf = true, playerId = "jonas")
    private val linkedInSelf = self.copy(timeFromLinkedIn = true)
    private val ghost = GhostRowModel("ref-ada", "Ada", 68_000L, hintFree = true)

    private fun show(showPuzzleBoxRank: Boolean = true) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column {
                    DailyBoardRow(real, RankOrder.TimeThenHints, place = 1, showPuzzleBoxRank = showPuzzleBoxRank)
                    DailyBoardRow(self, RankOrder.TimeThenHints, place = 2, showPuzzleBoxRank = showPuzzleBoxRank)
                    GhostRow(ghost, 3, RankOrder.TimeThenHints)
                }
            }
        }
    }

    private fun showLinkedInRows() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column {
                    DailyBoardRow(real, RankOrder.TimeThenHints, place = 1)
                    DailyBoardRow(linkedInSelf, RankOrder.TimeThenHints, place = 2)
                    GhostRow(ghost, 3, RankOrder.TimeThenHints)
                }
            }
        }
    }

    private fun left(tag: String): Dp = composeRule.onAllNodesWithTag(tag)[0].getBoundsInRoot().left

    @Test
    fun `a PuzzleBox row and a connection row draw photo, place, name and time in the same order`() {
        show()

        listOf("daily_board.row", "daily_board.ghost").forEach { row ->
            val order = listOf("photo", "rank", "name", "score").map { part -> left("$row.$part") }
            assertEquals("$row parts $order", order.sorted(), order)
            assertEquals("$row parts $order", 4, order.distinct().size)
        }
    }

    @Test
    fun `a row without a photo shows the name's initial in its photo slot`() {
        show()

        composeRule.onNode(hasText("M") and hasAnyAncestor(hasTestTag("daily_board.row.photo"))).assertExists()
        composeRule.onNode(hasText("J") and hasAnyAncestor(hasTestTag("daily_board.row.photo"))).assertExists()
        composeRule.onNode(hasText("A") and hasAnyAncestor(hasTestTag("daily_board.ghost.photo"))).assertExists()
    }

    @Test
    fun `every row has a photo slot, including the player's own`() {
        show()

        composeRule.onAllNodesWithTag("daily_board.row.photo").assertCountEquals(2)
        composeRule.onAllNodesWithTag("daily_board.ghost.photo").assertCountEquals(1)
    }

    @Test
    fun `a row with a second line and one without are the same size`() {
        show(showPuzzleBoxRank = false)
        val plain = composeRule.onAllNodesWithTag("daily_board.row")[0].getBoundsInRoot()
        val connection = composeRule.onNodeWithTag("daily_board.ghost").getBoundsInRoot()

        assertEquals(connection.height, plain.height)
        assertEquals(connection.width, plain.width)
        assertTrue("row $plain is a frame, not a hairline", plain.height >= 48.dp)
    }

    @Test
    fun `a row with its PuzzleBox rank line is the same height as without`() {
        show(showPuzzleBoxRank = true)
        val withLine = composeRule.onAllNodesWithTag("daily_board.row")[0].getBoundsInRoot().height
        composeRule.onNodeWithTag("daily_board.ghost").assertHeightIsEqualTo(withLine)
    }

    @Test
    fun `a connection row has the LinkedIn dot and no second line, a PuzzleBox row has no dot`() {
        showLinkedInRows()

        composeRule.onNodeWithTag("daily_board.ghost.linkedin_dot").assertExists()
        composeRule.onNodeWithTag("daily_board.ghost.detail").assertDoesNotExist()
        composeRule.onNode(hasText("on this phone", substring = true)).assertDoesNotExist()
        composeRule.onAllNodesWithTag("daily_board.row.linkedin_dot").assertCountEquals(1)
    }

    @Test
    fun `the player's own row with a LinkedIn time has the dot and keeps its highlight`() {
        showLinkedInRows()

        composeRule.onNode(hasTestTag("daily_board.row.linkedin_dot") and hasAnyAncestor(hasTestTag("daily_board.row.self")))
            .assertExists()
        composeRule.onAllNodesWithTag("daily_board.row.self").assertCountEquals(1)
    }

    @Test
    fun `the LinkedIn dot is a sticker on the photo that does not change the row's size`() {
        showLinkedInRows()
        val photo = composeRule.onNodeWithTag("daily_board.ghost.photo").getBoundsInRoot()
        val dot = composeRule.onNodeWithTag("daily_board.ghost.linkedin_dot").getBoundsInRoot()

        assertTrue("dot $dot sits on the photo's bottom-right corner $photo", dot.right >= photo.right - 2.dp && dot.bottom >= photo.bottom - 2.dp)
        assertEquals(composeRule.onAllNodesWithTag("daily_board.row")[0].getBoundsInRoot().height, composeRule.onNodeWithTag("daily_board.ghost").getBoundsInRoot().height)
    }
}

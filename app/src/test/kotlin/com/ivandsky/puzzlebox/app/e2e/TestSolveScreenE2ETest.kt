package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.gameui.DeviceClass
import com.ivandsky.puzzlebox.gameui.boardCapFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How the test solve's play screen (ticket 98) is drawn, over the real shell. A draft name too
 * long for the bar is cut with an ellipsis and "Test solve" stays whole after it. On a landscape tablet the board keeps to
 * its class's cap and the controls stack beside it, as they do in a daily.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class TestSolveScreenE2ETest {

    @Test
    fun `a draft name of the longest length is cut with an ellipsis and Test solve stays whole before the timer`() {
        check(LONG_NAME.length == Draft.TITLE_MAX)
        val world = TestSolveWorld()
        shellRobot(graph = world.graph) {
            world.startTestSolve(this, QueensCastle.board, LONG_NAME)
            awaitTag("play.sandbox")

            val label = inBar(hasText("Test solve", substring = true))
            rule.onNode(label, useUnmergedTree = true).assertIsDisplayed()
            val labelNode = rule.onNode(label, useUnmergedTree = true).fetchSemanticsNode()
            val labelText = labelNode.textLayout()
            assertEquals("Test solve is on one line", 1, labelText.lineCount)
            // Not hasVisualOverflow: a text that does not wrap is laid out as wide as its row, so it reads true for a whole label.
            val needed = labelText.multiParagraph.maxIntrinsicWidth
            assertFalse("Test solve is not cut with an ellipsis", labelText.isLineEllipsized(0))
            assertTrue(
                "Test solve needs $needed px and shows ${labelNode.boundsInRoot.width} of its ${labelText.size.width}",
                labelText.size.width >= needed && labelNode.boundsInRoot.width >= needed,
            )
            val timer = bounds("play.timer")
            assertTrue(
                "Test solve ends at ${labelNode.boundsInRoot.right}, before the timer at ${timer.left}",
                labelNode.boundsInRoot.right <= timer.left + 1f,
            )

            val name = rule.onNode(inBar(hasTextExactly(LONG_NAME)), useUnmergedTree = true).fetchSemanticsNode().textLayout()
            assertEquals("the name is on one line", 1, name.lineCount)
            assertTrue("the name is cut with an ellipsis", name.isLineEllipsized(0))
        }
    }

    @Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w1280dp-h800dp-land")
    @Test
    fun `on a landscape tablet the board keeps to its cap and the controls stack beside it`() {
        val world = TestSolveWorld()
        shellRobot(graph = world.graph) {
            world.startTestSolve(this, QueensCastle.board, "Castle")
            awaitTag("play.sandbox")

            val cap = with(rule.density) { boardCapFor(DeviceClass.TabletLandscape, QUEENS_COLUMNS).toPx() }
            val board = bounds("queens.board")
            assertEquals("the board is as wide as its cap", cap, board.width, 1f)

            val undo = bounds("play.undo")
            val redo = bounds("play.redo")
            val hint = bounds("play.hint")
            assertTrue("undo at ${undo.left} is beside the board, which ends at ${board.right}", undo.left >= board.right - 1f)
            assertTrue("undo, redo and hint stack top to bottom", undo.top < redo.top && redo.top < hint.top)
            assertTrue("the timer stays in the top bar", shows("play.timer"))
        }
    }

    /** [matcher], among the test solve screen's nodes. */
    private fun inBar(matcher: SemanticsMatcher): SemanticsMatcher = matcher and hasAnyAncestor(hasTestTag("play.sandbox"))

    private fun ShellRobot.bounds(tag: String): Rect {
        awaitTag(tag)
        return rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    }

    /** How this text node was laid out. */
    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private companion object {
        /** [Draft.TITLE_MAX] characters. */
        const val LONG_NAME = "The castle on the hill with six queens and a very long name."

        /** [QueensCastle] is six by six. */
        const val QUEENS_COLUMNS = 6
    }
}

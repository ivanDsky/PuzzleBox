package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.editor.QueensCrownIssue
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraftAnalyzer
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queens.editor.QueensPublishGates
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 25: the verdict card's Queens fix list — one row per reason, block or crown issue; Select selects their cells. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueensFixListTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a split region's Select puts exactly the block's cells in the selection`() {
        val draft = QueensDraft(SIZE, SPLIT)
        val blocks = QueensPublishGates.blocks(draft, Verdict.Checking)
        val split = blocks.single { it.id == PublishBlock.SPLIT_REGION }
        val controller = show(draft, Verdict.Checking, blocks)

        rule.onNodeWithTag(fixTag(PublishBlock.SPLIT_REGION)).assertExists()
        rule.onNodeWithTag(selectTag(PublishBlock.SPLIT_REGION)).performClick()

        assertEquals(split.cells, controller.selection.value)
        assertEquals(QueensEditorMode.Paint, controller.mode.value)
    }

    @Test
    fun `an Incomplete reason gets its row and unpainted cells their Select`() {
        val regions = WHOLE.toMutableList().apply { this[3] = QueensDraft.UNPAINTED; this[15] = QueensDraft.UNPAINTED }
        val draft = QueensDraft(SIZE, regions)
        val verdict = QueensDraftAnalyzer.analyze(draft, Budget.Unlimited)
        val controller = show(draft, verdict, emptyList())

        rule.onNodeWithTag(selectTag(QueensDraftAnalyzer.UNPAINTED)).performClick()

        assertEquals(setOf(3, 15), controller.selection.value)
    }

    @Test
    fun `two crowns in a region show the double row, whose Select selects the region and switches to Paint`() {
        val draft = QueensDraft(SIZE, WHOLE, crowns = setOf(0, 1, 7))
        val controller = show(draft, Verdict.Checking, emptyList())
        controller.setMode(QueensEditorMode.Crown)
        rule.waitForIdle()

        rule.onNodeWithTag(fixTag(QueensCrownIssue.DOUBLE)).assertExists()
        rule.onNodeWithTag(selectTag(QueensCrownIssue.DOUBLE)).performClick()

        assertEquals(setOf(0, 1), controller.selection.value)
        assertEquals(QueensEditorMode.Paint, controller.mode.value)
    }

    @Test
    fun `a painted region without a crown shows the missing row`() {
        // Region 0 holds two crowns, region 1 one; regions 2 and 3 hold none.
        show(QueensDraft(SIZE, WHOLE, crowns = setOf(0, 1, 7)), Verdict.Checking, emptyList())

        assertEquals(2, rule.onAllNodesWithTag(fixTag(QueensCrownIssue.MISSING)).fetchSemanticsNodes().size)
    }

    @Test
    fun `the same board without crowns shows neither crown row`() {
        show(QueensDraft(SIZE, WHOLE), Verdict.Checking, emptyList())

        rule.onNodeWithTag(fixTag(QueensCrownIssue.DOUBLE)).assertDoesNotExist()
        rule.onNodeWithTag(fixTag(QueensCrownIssue.MISSING)).assertDoesNotExist()
    }

    @Test
    fun `blocks the board does not own draw no Queens row`() {
        show(QueensDraft(SIZE, WHOLE), Verdict.Checking, listOf(PublishBlock(PublishBlock.NOT_TESTED)))

        rule.onNodeWithTag(fixTag(PublishBlock.NOT_TESTED)).assertDoesNotExist()
    }

    private fun show(draft: QueensDraft, verdict: Verdict, blocks: List<PublishBlock>): QueensEditorController {
        val controller = QueensEditorController(EditorDraft(QueensEditor, DraftState(draft))) {}
        rule.setContent {
            PuzzleBoxTheme { QueensEditorUi.Issues(controller, verdict, blocks, Modifier) }
        }
        return controller
    }

    private fun fixTag(id: String) = "queens.editor.fix.$id"

    private fun selectTag(id: String) = "queens.editor.fix.$id.select"

    private companion object {
        const val SIZE = 4

        /** Four regions, each in one piece: 0 = {0, 1}, 1 = {2..7}, 2 = {8, 9, 12, 13}, 3 = {10, 11, 14, 15}. */
        val WHOLE = listOf(
            0, 0, 1, 1,
            1, 1, 1, 1,
            2, 2, 3, 3,
            2, 2, 3, 3,
        )

        /** Region 0 in two pieces: {0, 1} and the stray {12}. */
        val SPLIT = listOf(
            0, 0, 1, 1,
            1, 1, 1, 1,
            2, 2, 3, 3,
            0, 2, 3, 3,
        )
    }
}

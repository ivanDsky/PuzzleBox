package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 23: the board flags what the verdict found — diff ring, clash fill — and split stripes after the settle pause. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueensEditorDiagnosticsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a Multiple verdict rings exactly the differing cells`() {
        val differing = setOf(0, 5, 10, 15)
        show(WHOLE, Verdict.Multiple(Solution(listOf(0, 5, 10, 15)), Solution(listOf(1, 4, 11, 14)), differing))

        assertEquals(differing, tagged("diff"))
        assertEquals(emptySet<Int>(), tagged("clash"))
    }

    @Test
    fun `a None verdict fills exactly the clash cells`() {
        val conflicts = setOf(2, 7)
        show(WHOLE, Verdict.None(conflicts))

        assertEquals(conflicts, tagged("clash"))
        assertEquals(emptySet<Int>(), tagged("diff"))
    }

    @Test
    fun `no verdict finding, no ring and no fill`() {
        show(WHOLE, Verdict.Checking)

        assertEquals(emptySet<Int>(), tagged("diff"))
        assertEquals(emptySet<Int>(), tagged("clash"))
    }

    @Test
    fun `a split region's stray piece gets stripes only after the settle pause`() {
        rule.mainClock.autoAdvance = false
        show(SPLIT, Verdict.Checking)

        rule.mainClock.advanceTimeBy(MotionTokens.SettleMillis - FRAME)
        assertEquals(emptySet<Int>(), tagged("split"))

        rule.mainClock.advanceTimeBy(2 * FRAME)
        assertEquals(setOf(12), tagged("split"))
    }

    @Test
    fun `a split made by an edit waits the pause, and a mended region loses its stripes at once`() {
        rule.mainClock.autoAdvance = false
        val controller = controllerOf(QueensDraft(SIZE, WHOLE))
        rule.setContent {
            PuzzleBoxTheme { QueensEditorBoard(controller, Verdict.Checking, Modifier.fillMaxWidth()) }
        }
        rule.mainClock.advanceTimeBy(MotionTokens.SettleMillis + FRAME)

        controller.adopt(EditorDraft(QueensEditor, DraftState(QueensDraft(SIZE, SPLIT))))
        rule.mainClock.advanceTimeBy(MotionTokens.SettleMillis / 2)
        assertEquals(emptySet<Int>(), tagged("split"))
        rule.mainClock.advanceTimeBy(MotionTokens.SettleMillis)
        assertEquals(setOf(12), tagged("split"))

        controller.adopt(EditorDraft(QueensEditor, DraftState(QueensDraft(SIZE, WHOLE))))
        rule.mainClock.advanceTimeBy(FRAME)
        assertEquals(emptySet<Int>(), tagged("split"))
    }

    private fun show(regions: List<Int>, verdict: Verdict) {
        val controller = controllerOf(QueensDraft(SIZE, regions))
        rule.setContent {
            PuzzleBoxTheme { QueensEditorBoard(controller, verdict, Modifier.fillMaxWidth()) }
        }
    }

    private fun controllerOf(draft: QueensDraft) =
        QueensEditorController(EditorDraft(QueensEditor, DraftState(draft))) {}

    /** Every cell carrying `queens.editor.cell.<idx>.<mark>`. */
    private fun tagged(mark: String): Set<Int> =
        (0 until SIZE * SIZE).filterTo(sortedSetOf()) { idx ->
            rule.onAllNodesWithTag("queens.editor.cell.$idx.$mark", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

    private companion object {
        const val SIZE = 4
        const val FRAME = 16L

        /** Four regions, each in one piece. */
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

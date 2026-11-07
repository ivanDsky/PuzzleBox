package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

/**
 * 23: the board flags what the verdict found — diff ring, clash fill — and split stripes after the settle pause.
 * 40: with answer crowns, a Multiple verdict ghosts the other answer and rings only the movable cells.
 */
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

    @Test
    fun `with crowns a Multiple verdict ghosts the other answer and rings only the movable cells`() {
        show(WHOLE, MULTIPLE, crowns = CROWNS)

        assertEquals(GHOSTS, tagged("ghost"))
        assertEquals(MOVABLE, tagged("diff"))
    }

    @Test
    fun `without crowns the same verdict ghosts nothing and rings every differing cell`() {
        show(WHOLE, MULTIPLE)

        assertEquals(emptySet<Int>(), tagged("ghost"))
        assertEquals(MULTIPLE.differingCells, tagged("diff"))
    }

    @Test
    fun `a first Checking shows no ghost and no ring`() {
        show(WHOLE, Verdict.Checking, crowns = CROWNS)

        assertEquals(emptySet<Int>(), tagged("ghost"))
        assertEquals(emptySet<Int>(), tagged("diff"))
    }

    @Test
    fun `Checking after Multiple keeps the ghosts and movable ring until a settled verdict clears them`() {
        var verdict: Verdict by mutableStateOf(MULTIPLE)
        val controller = controllerOf(QueensDraft(SIZE, WHOLE, CROWNS))
        rule.setContent { PuzzleBoxTheme { QueensEditorBoard(controller, verdict, Modifier.fillMaxWidth()) } }

        verdict = Verdict.Checking
        rule.waitForIdle()
        assertEquals(GHOSTS, tagged("ghost"))
        assertEquals(MOVABLE, tagged("diff"))

        verdict = Verdict.Unique(MULTIPLE.solutionA)
        rule.waitForIdle()
        assertEquals(emptySet<Int>(), tagged("ghost"))
        assertEquals(emptySet<Int>(), tagged("diff"))
    }

    @Test
    fun `a tap on a ghost cell in Crown mode crowns it, and a crossed ghost refuses as without the ghost`() {
        val controller = controllerOf(QueensDraft(SIZE, WHOLE, CROWNS)).apply { setMode(QueensEditorMode.Crown) }
        rule.setContent { PuzzleBoxTheme { QueensEditorBoard(controller, MULTIPLE, Modifier.fillMaxWidth()) } }

        rule.onNodeWithTag("queens.editor.cell.$CROSSED_GHOST", useUnmergedTree = true).performClick()
        assertEquals(CROWNS, crownsOf(controller))

        rule.onNodeWithTag("queens.editor.cell.$OPEN_GHOST", useUnmergedTree = true).performClick()
        assertEquals(CROWNS + OPEN_GHOST, crownsOf(controller))
    }

    private fun show(regions: List<Int>, verdict: Verdict, crowns: Set<Int> = emptySet()) {
        val controller = controllerOf(QueensDraft(SIZE, regions, crowns))
        rule.setContent {
            PuzzleBoxTheme { QueensEditorBoard(controller, verdict, Modifier.fillMaxWidth()) }
        }
    }

    private fun controllerOf(draft: QueensDraft) =
        QueensEditorController(EditorDraft(QueensEditor, DraftState(draft))) {}

    private fun crownsOf(controller: QueensEditorController): Set<Int> =
        (controller.draft.value.state.draft as QueensDraft).crowns

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

        /** The author's crowns sit on witness A; B shares 15 with A. */
        val CROWNS = setOf(0, 5)
        val MULTIPLE = Verdict.Multiple(
            Solution(listOf(0, 5, 10, 15)),
            Solution(listOf(1, 4, 11, 15)),
            setOf(0, 1, 4, 5, 10, 11),
        )

        /** B's queens minus the crowns; 15 sits inside region 3, so it is the one ghost that is not movable. */
        val GHOSTS = setOf(1, 4, 11, 15)
        val MOVABLE = setOf(1, 4, 11)

        /** Crown 0 crosses 1 (same row and region); nothing crosses 11. */
        const val CROSSED_GHOST = 1
        const val OPEN_GHOST = 11
    }
}

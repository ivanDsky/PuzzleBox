package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensAnswerFit
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraftAnalyzer
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ticket 39: the coach line and probe strip under the board; their reserved height keeps the board still. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h470dp-420dpi")
class QueensCoachLineTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `each coach state renders its own tag and Hidden renders none`() {
        var state: QueensCoachState by mutableStateOf(QueensCoachState.Hidden)
        rule.setContent { PuzzleBoxTheme { QueensCoachLine(state, reducedMotion = true) } }

        val cases = listOf(
            QueensCoachState.PlaceCrowns(2, SIZE) to "place",
            QueensCoachState.PaintRegions to "paint",
            QueensCoachState.Ambiguous to "ambiguous",
            QueensCoachState.Unique to "unique",
        )
        assertCoach(null)
        for ((each, tag) in cases) {
            state = each
            rule.waitForIdle()
            assertCoach(tag)
        }
        state = QueensCoachState.Hidden
        rule.waitForIdle()
        assertCoach(null)
    }

    @Test
    fun `Kept and Broken render their probe tag, Silent and Ambiguous render none`() {
        var fit: QueensAnswerFit by mutableStateOf(QueensAnswerFit.Silent)
        rule.setContent { PuzzleBoxTheme { QueensProbeStrip(fit, crowns = 3) } }

        assertProbe(null)
        fit = QueensAnswerFit.Kept
        rule.waitForIdle()
        assertProbe("kept")
        fit = QueensAnswerFit.Broken
        rule.waitForIdle()
        assertProbe("broken")
        fit = QueensAnswerFit.Ambiguous(authorIsA = true, ghosts = setOf(1), movable = setOf(1))
        rule.waitForIdle()
        assertProbe(null)
    }

    @Test
    fun `the board keeps its height from Incomplete through Multiple to Unique with crowns`() {
        val controller = QueensEditorController(crowned()) {}
        var verdict: Verdict by mutableStateOf(INCOMPLETE)
        rule.setContent {
            PuzzleBoxTheme { QueensEditorBoard(controller, verdict, Modifier.fillMaxSize()) }
        }

        assertCoach("paint")
        assertProbe(null)
        val incomplete = boardHeight()

        verdict = Verdict.Multiple(Solution(CROWNS), Solution(OTHER), CROWNS.toSet() + OTHER.toSet())
        rule.waitForIdle()
        assertCoach("ambiguous")
        assertProbe(null)
        assertEquals(incomplete, boardHeight())

        verdict = Verdict.Unique(Solution(CROWNS))
        rule.waitForIdle()
        assertCoach("unique")
        assertProbe("kept")
        assertEquals(incomplete, boardHeight())
    }

    @Test
    fun `crown mode shows the coach instead of the paint hint and the tool strip keeps its height`() {
        val controller = QueensEditorController(EditorDraft.fresh(QueensEditor, QueensVariants.variant(SIZE).id)) {}
        rule.setContent {
            PuzzleBoxTheme {
                Column(Modifier.fillMaxSize()) {
                    QueensEditorUi.ToolHint(controller, Modifier.fillMaxWidth())
                    QueensEditorBoard(controller, INCOMPLETE, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }

        assertCoach("place")
        rule.onAllNodesWithTag(HINT).assertCountEquals(0)
        val crownStrip = height(STRIP)
        val crownBoard = boardHeight()

        rule.onNodeWithTag(PAINT_MODE).performClick()

        rule.onAllNodesWithTag(HINT).assertCountEquals(1)
        assertCoach(null)
        assertEquals(crownStrip, height(STRIP))
        assertEquals(crownBoard, boardHeight())
    }

    private fun assertCoach(tag: String?) {
        for (each in COACH_TAGS) {
            rule.onAllNodesWithTag("queens.editor.coach.$each").assertCountEquals(if (each == tag) 1 else 0)
        }
    }

    private fun assertProbe(tag: String?) {
        for (each in PROBE_TAGS) {
            rule.onAllNodesWithTag("queens.editor.probe.$each").assertCountEquals(if (each == tag) 1 else 0)
        }
    }

    private fun height(tag: String): Dp = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private fun boardHeight(): Dp = height(BOARD)

    /** A 6×6 board painted by rows with [CROWNS] placed: it opens in Paint. */
    private fun crowned(): EditorDraft<QueensDraft> {
        val blank = EditorDraft.fresh(QueensEditor, QueensVariants.variant(SIZE).id)
        val board = blank.state.draft.copy(regions = List(SIZE * SIZE) { it / SIZE }, crowns = CROWNS.toSet())
        return EditorDraft(QueensEditor, blank.state.copy(draft = board))
    }

    private companion object {
        const val SIZE = 6
        const val BOARD = "queens.editor.board"
        const val STRIP = "queens.editor.mode"
        const val PAINT_MODE = "queens.editor.mode.paint"
        const val HINT = "queens.editor.context.hint"
        val COACH_TAGS = listOf("place", "paint", "ambiguous", "unique")
        val PROBE_TAGS = listOf("kept", "broken")
        val CROWNS = listOf(1, 9, 17, 18, 26, 34)
        val OTHER = listOf(4, 6, 14, 23, 25, 33)
        val INCOMPLETE = Verdict.Incomplete(IncompleteReason(QueensDraftAnalyzer.REGIONS, 6))
    }
}

package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.ivandsky.puzzlebox.games.queensui.QueensRegionPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    fun `the Broken chip sits on the error container, apart from Kept, with readable ink`() {
        val seen = mutableMapOf<Boolean, Triple<ColorScheme, Pair<Color, Color>?, Pair<Color, Color>?>>()
        rule.setContent {
            for (dark in listOf(false, true)) {
                PuzzleBoxTheme(darkTheme = dark) {
                    seen[dark] = Triple(
                        MaterialTheme.colorScheme,
                        probeChipColors(QueensAnswerFit.Kept),
                        probeChipColors(QueensAnswerFit.Broken),
                    )
                }
            }
        }
        rule.waitForIdle()
        for ((dark, chips) in seen) {
            val (scheme, kept, broken) = chips
            val (ground, ink) = checkNotNull(broken)
            assertEquals("dark=$dark", scheme.errorContainer to scheme.onErrorContainer, ground to ink)
            assertNotEquals("dark=$dark", checkNotNull(kept).first, ground)
            assertTrue("dark=$dark", QueensRegionPalette.contrast(ink, ground) >= GLYPH_BAR)
        }
        assertEquals(2, seen.size)
    }

    @Test
    fun `the coach reserves two lines, so the longest copy wraps unclipped at 320 dp`() {
        var state: QueensCoachState by mutableStateOf(QueensCoachState.Ambiguous)
        rule.setContent { PuzzleBoxTheme { QueensCoachLine(state, reducedMotion = true) } }

        val ambiguous = rule.onNodeWithTag("queens.editor.coach.ambiguous")
        val layout = mutableListOf<TextLayoutResult>()
        ambiguous.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        assertEquals(2, layout.single().lineCount)
        assertTrue(!layout.single().hasVisualOverflow)
        val twoLines = height("queens.editor.coach.ambiguous")

        state = QueensCoachState.Unique
        rule.waitForIdle()
        assertEquals(twoLines, height("queens.editor.coach.unique"))
    }

    @Test
    fun `in a tall slot the grid takes the full width and the rows sit below it`() {
        val controller = QueensEditorController(crowned()) {}
        rule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.width(SLOT_WIDTH).height(SLOT_HEIGHT)) {
                    QueensEditorBoard(controller, Verdict.Unique(Solution(CROWNS)), Modifier.fillMaxSize())
                }
            }
        }

        val grid = rule.onNodeWithTag(BOARD).getUnclippedBoundsInRoot()
        assertEquals(SLOT_WIDTH, grid.right - grid.left)
        assertEquals(SLOT_WIDTH, grid.bottom - grid.top)
        assertTrue(rule.onNodeWithTag("queens.editor.coach.unique").getUnclippedBoundsInRoot().top >= grid.bottom)
    }

    @Test
    @Config(qualifiers = "w600dp-h470dp-420dpi")
    fun `in a wide slot the rows under the grid start and end at its edges`() {
        val controller = QueensEditorController(crowned()) {}
        rule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.width(SLOT_HEIGHT).height(SLOT_WIDTH)) {
                    QueensEditorBoard(controller, Verdict.Unique(Solution(CROWNS)), Modifier.fillMaxSize())
                }
            }
        }

        val grid = rule.onNodeWithTag(BOARD).getUnclippedBoundsInRoot()
        val coach = rule.onNodeWithTag("queens.editor.coach.unique").getUnclippedBoundsInRoot()
        assertTrue("grid ${grid.right - grid.left} fills the slot", grid.right - grid.left < SLOT_HEIGHT)
        assertEquals(grid.left, coach.left)
        assertEquals(grid.right, coach.right)
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

        verdict = Verdict.Checking
        rule.waitForIdle()
        assertCoach("ambiguous")

        verdict = Verdict.Unique(Solution(CROWNS))
        rule.waitForIdle()
        assertCoach("unique")
        assertProbe("kept")
        assertEquals(incomplete, boardHeight())
    }

    @Test
    fun `a Kept probe stays Kept through Checking`() {
        val controller = QueensEditorController(crowned()) {}
        var verdict: Verdict by mutableStateOf(Verdict.Unique(Solution(CROWNS)))
        rule.setContent {
            PuzzleBoxTheme { QueensEditorBoard(controller, verdict, Modifier.fillMaxSize()) }
        }
        assertProbe("kept")

        verdict = Verdict.Checking
        rule.waitForIdle()

        assertProbe("kept")
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
        const val GLYPH_BAR = 4.5f
        const val BOARD = "queens.editor.board"
        const val STRIP = "queens.editor.mode"
        const val PAINT_MODE = "queens.editor.mode.paint"
        const val HINT = "queens.editor.context.hint"
        val COACH_TAGS = listOf("place", "paint", "ambiguous", "unique")
        val PROBE_TAGS = listOf("kept", "broken")
        val CROWNS = listOf(1, 9, 17, 18, 26, 34)
        val OTHER = listOf(4, 6, 14, 23, 25, 33)
        val INCOMPLETE = Verdict.Incomplete(IncompleteReason(QueensDraftAnalyzer.REGIONS, 6))
        val SLOT_WIDTH = 320.dp
        val SLOT_HEIGHT = 460.dp
    }
}

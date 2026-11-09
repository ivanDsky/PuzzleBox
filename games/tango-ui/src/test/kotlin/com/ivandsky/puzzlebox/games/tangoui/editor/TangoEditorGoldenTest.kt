package com.ivandsky.puzzlebox.games.tangoui.editor

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraftAnalyzer
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import com.ivandsky.puzzlebox.games.tango.editor.TangoForcedCells
import com.ivandsky.puzzlebox.games.tango.editor.TangoPublishGates
import com.ivandsky.puzzlebox.games.tangoui.GoldenCompare
import com.ivandsky.puzzlebox.games.tangoui.TestInlineDispatcherProvider
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.GridEdge
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Tango editor's looks on a 411 dp phone and in the 1280 dp tablet landscape, light and
 * dark: empty, the press preview on a square and on an edge, Tap zones, Forced cells over a
 * partly forced board, Unique with its label, Unique needing guessing, Multiple with the
 * differ shade, None with its clashes and the witness drawn on the board. Each board is a
 * real draft whose verdict comes from [TangoDraftAnalyzer], asserted before the capture.
 *
 * Every run captures to a scratch file. `-Proborazzi.verify=true` pixel-compares it with the
 * committed image in `src/test/resources/goldens/editor/`; drift or a missing image fails.
 * `-Proborazzi.record=true` rewrites the images; the default run reads and writes none.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TangoEditorGoldenTestPhone : TangoEditorGoldenTest("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class TangoEditorGoldenTestTablet : TangoEditorGoldenTest("tablet")

abstract class TangoEditorGoldenTest(private val device: String) {

    @get:Rule
    val rule = createComposeRule()

    private val mode = System.getProperty("tango.golden.mode") ?: "compare"
    private val pixelCompare = System.getProperty("goldens.pixelCompare") != "false"

    @Test fun `empty light`() = empty(dark = false)
    @Test fun `empty dark`() = empty(dark = true)

    @Test fun `press on a square light`() = pressSquare(dark = false)
    @Test fun `press on a square dark`() = pressSquare(dark = true)

    @Test fun `press on an edge light`() = pressEdge(dark = false)
    @Test fun `press on an edge dark`() = pressEdge(dark = true)

    @Test fun `tap zones light`() = tapZones(dark = false)
    @Test fun `tap zones dark`() = tapZones(dark = true)

    @Test fun `forced cells light`() = forcedCells(dark = false)
    @Test fun `forced cells dark`() = forcedCells(dark = true)

    @Test fun `unique with label light`() = unique(dark = false)
    @Test fun `unique with label dark`() = unique(dark = true)

    @Test fun `needs guessing light`() = needsGuessing(dark = false)
    @Test fun `needs guessing dark`() = needsGuessing(dark = true)

    @Test fun `multiple differ shade light`() = multiple(dark = false)
    @Test fun `multiple differ shade dark`() = multiple(dark = true)

    @Test fun `none clashes light`() = none(dark = false)
    @Test fun `none clashes dark`() = none(dark = true)

    @Test fun `witness on board light`() = witness(dark = false)
    @Test fun `witness on board dark`() = witness(dark = true)

    private fun empty(dark: Boolean) {
        val draft = TangoDraft(6, emptyMap(), emptyList())
        capture("empty", dark, draft, analyzed<Verdict.Incomplete>(draft))
    }

    private fun pressSquare(dark: Boolean) {
        val draft = SAMPLE
        capture("press-square", dark, draft, analyzed<Verdict.Unique>(draft)) {
            press(TangoEditorTarget.Square(20))
        }
    }

    private fun pressEdge(dark: Boolean) {
        val draft = SAMPLE
        val edge = GridEdge(GridCell(row = 2, col = 3), GridEdge.Side.Right)
        capture("press-edge", dark, draft, analyzed<Verdict.Unique>(draft)) {
            press(TangoEditorTarget.Edge(edge))
        }
    }

    private fun tapZones(dark: Boolean) {
        capture("tap-zones", dark, SAMPLE, analyzed<Verdict.Unique>(SAMPLE)) { toggleTapZones() }
    }

    /** STUCK: unique, so no differ shade; the hint ladder forces some cells, then stalls. */
    private fun forcedCells(dark: Boolean) {
        val forced = TangoForcedCells.of(STUCK)
        assertTrue("the board forces some cells", forced.isNotEmpty())
        assertTrue("the board is not forced whole", forced.size + STUCK.givens.size < CELLS)
        capture("forced-cells", dark, STUCK, analyzed<Verdict.Unique>(STUCK)) { toggleForcedView() }
    }

    private fun unique(dark: Boolean) {
        val verdict = analyzed<Verdict.Unique>(SAMPLE)
        assertTrue("the board has a label", verdict.difficulty != null && !verdict.needsGuessing)
        capture("unique-label", dark, SAMPLE, verdict)
    }

    private fun needsGuessing(dark: Boolean) {
        val verdict = analyzed<Verdict.Unique>(STUCK)
        assertTrue("no rung finishes it", verdict.needsGuessing && verdict.difficulty == null)
        capture("needs-guessing", dark, STUCK, verdict)
    }

    private fun multiple(dark: Boolean) {
        val draft = TangoDraft(6, mapOf(0 to TangoCell.SUN), emptyList())
        capture("multiple-differ", dark, draft, analyzed<Verdict.Multiple>(draft))
    }

    private fun none(dark: Boolean) {
        val draft = TangoDraft(6, (0..2).associateWith { TangoCell.SUN }, emptyList())
        val verdict = analyzed<Verdict.None>(draft)
        assertEquals("the three suns clash", setOf(0, 1, 2), verdict.conflicts)
        capture("none-clashes", dark, draft, verdict)
    }

    private fun witness(dark: Boolean) {
        val draft = TangoDraft(6, mapOf(0 to TangoCell.SUN), emptyList())
        capture("witness-on-board", dark, draft, analyzed<Verdict.Multiple>(draft)) {
            adoptOverlay(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true))
        }
    }

    private inline fun <reified V : Verdict> analyzed(draft: TangoDraft): V {
        val verdict = TangoDraftAnalyzer.analyze(draft, Budget.Unlimited)
        assertTrue("expected ${V::class.simpleName}, got $verdict", verdict is V)
        return verdict as V
    }

    private fun capture(
        name: String,
        dark: Boolean,
        draft: TangoDraft,
        verdict: Verdict,
        setUp: TangoEditorController.() -> Unit = {},
    ) {
        val controller = TangoEditorController(EditorDraft(TangoEditor, DraftState(draft))) {}
        controller.setUp()
        val blocks = TangoPublishGates.blocks(draft, verdict)
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides config,
                LocalForcedDispatchers provides TestInlineDispatcherProvider,
            ) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.testTag(GOLDEN)) {
                        Column(Modifier.padding(Spacing.md).then(frame()), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (device == "tablet") {
                                // The Studio's SquareBoardSlot: the smaller of the pane's width and height, filled.
                                Box(Modifier.size(minOf(TabletPaneWidth, TabletPaneHeight))) {
                                    TangoEditorUi.Board(controller, verdict, Modifier.fillMaxSize())
                                }
                            } else {
                                TangoEditorUi.Board(controller, verdict, Modifier.fillMaxWidth())
                            }
                            TangoEditorUi.ToolHint(controller, Modifier.fillMaxWidth())
                            TangoEditorUi.Issues(
                                controller,
                                verdict,
                                blocks,
                                onSelect = {},
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val stem = stem(name, dark)
        val scratch = File("build/roborecord/editor/$stem.png").apply { parentFile?.mkdirs() }
        rule.onNodeWithTag(GOLDEN).captureRoboImage(scratch)
        GoldenCompare.check(File("src/test/resources/goldens/editor/$stem.png"), scratch, stem)
    }

    private fun frame(): Modifier =
        if (device == "tablet") Modifier.width(TabletPaneWidth) else Modifier.width(PhoneWidth)

    private fun stem(name: String, dark: Boolean) = "$name-$device-${if (dark) "dark" else "light"}"

    private companion object {
        const val GOLDEN = "golden"
        const val CELLS = 6 * 6

        /** The 411 dp phone less the capture's padding. */
        val PhoneWidth = 379.dp

        /** The tablet Studio's left pane (1280 − the 372 dp side pane) less the padding. */
        val TabletPaneWidth = 876.dp

        /** The tablet Studio's pane height: the board slot's square is the smaller side. */
        val TabletPaneHeight = 720.dp

        /** The bundled sample: unique, graded by its hardest rung. */
        val SAMPLE = TangoDraft(
            6,
            givens = listOf(0, 1, 6, 7, 14, 16, 22, 29, 32).associateWith { TangoCell.SUN } +
                (33 to TangoCell.MOON),
            markers = listOf(
                TangoMarker(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoMarker(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                TangoMarker(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            ),
        )

        /** A unique board no hint rung forces. */
        val STUCK = TangoDraft(
            6,
            givens = mapOf(
                0 to TangoCell.SUN, 7 to TangoCell.SUN, 15 to TangoCell.MOON,
                23 to TangoCell.MOON, 34 to TangoCell.SUN, 35 to TangoCell.SUN,
            ),
            markers = listOf(
                TangoMarker(1, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(5, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(12, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                TangoMarker(13, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                TangoMarker(16, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(19, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
            ),
        )
    }
}

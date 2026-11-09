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
import com.ivandsky.puzzlebox.game.editor.NotationResult
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
import com.ivandsky.puzzlebox.gameui.PlayLayout
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

    @Test fun `empty 4x4 light`() = emptySized(4, dark = false)
    @Test fun `empty 4x4 dark`() = emptySized(4, dark = true)

    @Test fun `empty 8x8 light`() = emptySized(8, dark = false)
    @Test fun `empty 8x8 dark`() = emptySized(8, dark = true)

    @Test fun `empty 10x10 light`() = emptySized(10, dark = false)
    @Test fun `empty 10x10 dark`() = emptySized(10, dark = true)

    @Test fun `unique 8x8 light`() = unique8(dark = false)
    @Test fun `unique 8x8 dark`() = unique8(dark = true)

    @Test fun `tap zones 10x10 light`() = tapZones10(dark = false)
    @Test fun `tap zones 10x10 dark`() = tapZones10(dark = true)

    @Test fun `forced cells 10x10 light`() = forcedCells10(dark = false)
    @Test fun `forced cells 10x10 dark`() = forcedCells10(dark = true)

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

    private fun emptySized(n: Int, dark: Boolean) {
        val draft = TangoDraft(n, emptyMap(), emptyList())
        capture("empty-n$n", dark, draft, analyzed<Verdict.Incomplete>(draft))
    }

    /** The 8×8 fixture: givens and markers, unique. */
    private fun unique8(dark: Boolean) {
        val draft = UNIQUE_8
        assertTrue("the board has givens and markers", draft.givens.isNotEmpty() && draft.markers.isNotEmpty())
        capture("unique-n8", dark, draft, analyzed<Verdict.Unique>(draft))
    }

    private fun tapZones10(dark: Boolean) {
        val draft = UNIQUE_10
        assertTrue("the board has at least 20 givens", draft.givens.size >= 20)
        capture("tap-zones-n10", dark, draft, analyzed<Verdict.Unique>(draft)) { toggleTapZones() }
    }

    /** The fixture is forced whole by the hint ladder, so every empty cell wears a forced mark. */
    private fun forcedCells10(dark: Boolean) {
        val draft = UNIQUE_10
        val forced = TangoForcedCells.of(draft)
        assertTrue("the board forces some cells", forced.isNotEmpty())
        assertEquals("every empty cell is forced", 10 * 10, forced.size + draft.givens.size)
        capture("forced-cells-n10", dark, draft, analyzed<Verdict.Unique>(draft)) { toggleForcedView() }
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
                                    TangoEditorUi.Board(controller, verdict, PlayLayout.TwoPane, Modifier.fillMaxSize())
                                }
                            } else {
                                TangoEditorUi.Board(controller, verdict, PlayLayout.SinglePane, Modifier.fillMaxWidth())
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

        /** The rows and marker lines of `games/tango/src/test/resources/notation/tango-unique-8x8.txt`. */
        val UNIQUE_8 = notation(
            """
            .....S..
            ...S..S.
            S.......
            ...M....
            S....S..
            ...S...M
            ........
            ..M...M.
            = 1,2 down
            x 2,5 right
            x 3,3 down
            = 5,2 down
            = 5,6 down
            = 5,7 down
            x 6,2 right
            x 6,3 right
            x 7,4 right
            x 8,4 right
            x 8,7 right
            """,
        )

        /** The rows and marker lines of `games/tango/src/test/resources/notation/tango-unique-10x10.txt`. */
        val UNIQUE_10 = notation(
            """
            ...S..M..S
            .M..SS....
            .........M
            M.....M...
            ...S....SS
            ..........
            SS...M....
            S.S.......
            ....M..M..
            ..M.......
            x 2,6 right
            x 3,2 right
            = 3,8 down
            = 4,6 down
            = 5,2 right
            x 5,7 down
            = 5,8 down
            x 6,2 down
            = 6,3 right
            x 6,8 right
            = 6,9 right
            x 7,4 right
            x 8,2 down
            x 8,4 down
            = 8,9 right
            x 9,6 right
            x 9,8 down
            x 10,5 right
            """,
        )

        private fun notation(text: String): TangoDraft =
            when (val parsed = TangoEditor.parse(text.trimIndent().trim())) {
                is NotationResult.Ok -> parsed.draft
                is NotationResult.Err -> error("fixture does not parse: $parsed")
            }

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

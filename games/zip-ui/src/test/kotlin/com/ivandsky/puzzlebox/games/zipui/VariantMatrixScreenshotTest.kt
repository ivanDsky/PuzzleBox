package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultScreen
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard as ZipBoardModel
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The T25 variant matrix (spec UIUX-01 A6/A7/A12, success criterion 3): the panes the
 * user judges remotely on the review page. Every board pane renders the PRODUCTION
 * [com.ivandsky.puzzlebox.games.zipui.ZipBoard] through the real `ZipRendererModel.of`
 * mapping; every decision-variant value comes from [ZipBoardSpec] /
 * [LocalBoardColors] — nothing here retunes production (the gate contract: ZipBoardSpec
 * retunes happen ONLY after the user picks). Captures write straight to the review-page
 * diagrams tree (`design/diagrams/matrix/`); they are gate evidence, not goldens — no
 * verify pinning.
 *
 * One capture per test method (the compose rule's content cannot be re-set within a
 * test). Phone pane: `w360dp` with the play screen's real margins (Spacing.lg), so the
 * n8 pane IS the measured worst case (board 328dp → pitch 41dp → two-digit cap 9.35dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi")
class VariantMatrixScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ---------------------------------------------------------------------------
    // Board matrix: n5–n8 × {fresh, trace, won} × {dark, light} — 24 panes.
    // Real phone pane: 360dp frame − 2 × Spacing.lg margins = 328dp board at every n,
    // so the panes show the true relative pitch (the legibility worst case is n8).
    // ---------------------------------------------------------------------------

    @Test fun `n5 fresh dark`() = boardPane(5, State.FRESH, dark = true)
    @Test fun `n5 fresh light`() = boardPane(5, State.FRESH, dark = false)
    @Test fun `n5 trace dark`() = boardPane(5, State.TRACE, dark = true)
    @Test fun `n5 trace light`() = boardPane(5, State.TRACE, dark = false)
    @Test fun `n5 won dark`() = boardPane(5, State.WON, dark = true)
    @Test fun `n5 won light`() = boardPane(5, State.WON, dark = false)

    @Test fun `n6 fresh dark`() = boardPane(6, State.FRESH, dark = true)
    @Test fun `n6 fresh light`() = boardPane(6, State.FRESH, dark = false)
    @Test fun `n6 trace dark`() = boardPane(6, State.TRACE, dark = true)
    @Test fun `n6 trace light`() = boardPane(6, State.TRACE, dark = false)
    @Test fun `n6 won dark`() = boardPane(6, State.WON, dark = true)
    @Test fun `n6 won light`() = boardPane(6, State.WON, dark = false)

    @Test fun `n7 fresh dark`() = boardPane(7, State.FRESH, dark = true)
    @Test fun `n7 fresh light`() = boardPane(7, State.FRESH, dark = false)
    @Test fun `n7 trace dark`() = boardPane(7, State.TRACE, dark = true)
    @Test fun `n7 trace light`() = boardPane(7, State.TRACE, dark = false)
    @Test fun `n7 won dark`() = boardPane(7, State.WON, dark = true)
    @Test fun `n7 won light`() = boardPane(7, State.WON, dark = false)

    @Test fun `n8 fresh dark`() = boardPane(8, State.FRESH, dark = true)
    @Test fun `n8 fresh light`() = boardPane(8, State.FRESH, dark = false)
    @Test fun `n8 trace dark`() = boardPane(8, State.TRACE, dark = true)
    @Test fun `n8 trace light`() = boardPane(8, State.TRACE, dark = false)
    @Test fun `n8 won dark`() = boardPane(8, State.WON, dark = true)
    @Test fun `n8 won light`() = boardPane(8, State.WON, dark = false)

    private enum class State { FRESH, TRACE, WON }

    private fun boardPane(n: Int, state: State, dark: Boolean) {
        val snake = snake(n)
        val path = when (state) {
            State.FRESH -> emptyList()
            State.TRACE -> snake.take(snake.size * 55 / 100)
            State.WON -> snake
        }
        val model = stateModel(n, gateWaypoints(n, snake), path)
        capture("board-n$n-${state.name.lowercase()}-${theme(dark)}.png", dark) {
            ZipBoard(
                model,
                Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Result screen: the T23 route surface, both themes (record badge shown —
    // the accent moment is what needs judging).
    // ---------------------------------------------------------------------------

    @Test fun `result dark`() = resultPane(dark = true)

    @Test fun `result light`() = resultPane(dark = false)

    private fun resultPane(dark: Boolean) {
        capture("result-${theme(dark)}.png", dark) {
            ResultScreen(
                timeMs = 95_000,
                hintsUsed = 1,
                onPlayAgain = {},
                verdict = RecordVerdict.NewBest,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Decision variants (gate checklist): side-by-side panes, clearly labeled.
    // ---------------------------------------------------------------------------

    // T22's registered gate swatch: the U1-measured red→orange gradient next to the
    // flat pathTrace token, same S-curve.
    @Test fun `variant trace gradient vs token dark`() = capture("variant-trace-gradient-vs-token-dark.png", darkTheme = true) {
        ZipTraceGradientVsTokenPreview()
    }

    @Test fun `variant trace gradient vs token light`() = capture("variant-trace-gradient-vs-token-light.png", darkTheme = false) {
        ZipTraceGradientVsTokenPreview()
    }

    @Test fun `variant start hollow dark`() = startVariant(dark = true)

    @Test fun `variant start hollow light`() = startVariant(dark = false)

    private fun startVariant(dark: Boolean) {
        capture("variant-start-filled-vs-hollow-${theme(dark)}.png", dark) {
            // Real renderer, 3×3 fresh board, the center cell as waypoint 1. The
            // current build draws the start hollow (T22); the alternative is the same
            // board with the start flag off — a plain filled dot. Pure data
            // difference, identical geometry.
            val hollow = stateModel(3, waypoints = listOf(4), path = emptyList())
            val filled = hollow.copy(cells = hollow.cells.map { it.copy(isStart = false) })
            Row(
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabeledPane("current: hollow ring (T22)", paneSize = 128.dp) {
                    ZipBoard(hollow, Modifier.size(128.dp))
                }
                LabeledPane("alternative: filled", paneSize = 128.dp) {
                    ZipBoard(filled, Modifier.size(128.dp))
                }
            }
        }
    }

    @Test fun `variant light selection step`() {
        // A light-token-only decision (#D07C00 current vs #C27400 candidate, both on
        // white). Test-only hexes: the alternative is a gate candidate, not a token.
        capture("variant-selection-step-light.png", darkTheme = false) {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "boardSelection, light (3:1 non-text bar on #FFFFFF)",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                    SelectionSwatch(Color(0xFFD07C00), "current #D07C00 · 3.20:1")
                    SelectionSwatch(Color(0xFFC27400), "candidate #C27400 · 3.63:1")
                }
                Text(
                    "token swatch — the on-board selection consumer lands in wave 2 (drag)",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers.
    // ---------------------------------------------------------------------------

    private fun theme(dark: Boolean) = if (dark) "dark" else "light"

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        MATRIX_DIR.mkdirs()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = darkTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        }
        composeRule.onRoot().captureRoboImage(File(MATRIX_DIR, name).absolutePath)
    }

    /** Boustrophedon (snake) Hamiltonian path — the `won` panes' full-coverage trace. */
    private fun snake(n: Int): List<Int> = buildList {
        for (row in 0 until n) {
            val base = row * n
            if (row % 2 == 0) addAll(base until base + n) else addAll(base + n - 1 downTo base)
        }
    }

    /**
     * Waypoints 1..k in snake order — first at the snake head, last at the final cell —
     * so the full snake IS a win (waypoint order + end-at-last-waypoint hold). k = the
     * real daily census per size (n8 = 16, labels "10"–"16" included).
     */
    private fun gateWaypoints(n: Int, snake: List<Int>): List<Int> {
        val count = when (n) {
            5 -> 5
            6 -> 6
            7 -> 8
            else -> 16
        }
        return List(count) { i -> snake[i * (snake.size - 1) / (count - 1)] }
    }

    /** The real production mapping (`ZipRendererModel.of`), so cells/status track the path exactly as in play. */
    private fun stateModel(n: Int, waypoints: List<Int>, path: List<Int>): ZipRendererModel {
        val board = ZipBoardModel(n, emptySet(), waypoints.map(::Cell))
        val state = ZipState(ZipPuzzle(VariantId("zip/n$n"), board, null), path.map(::Cell))
        return ZipRendererModel.of(state, null)
    }

    @Composable
    private fun LabeledPane(label: String, paneSize: Dp, pane: @Composable BoxScope.() -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(Modifier.size(paneSize), contentAlignment = Alignment.Center, content = pane)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }

    @Composable
    private fun SelectionSwatch(color: Color, label: String) {
        val board = LocalBoardColors.current
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .background(board.boardSurface, RoundedCornerShape(10.dp))
                    .border(1.5.dp, board.boardGridLine, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Box(Modifier.fillMaxSize().background(color, RoundedCornerShape(10.dp)))
            }
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }

    companion object {
        // The review-page tree served on :8777 (user.dir = the module dir under Gradle).
        private val MATRIX_DIR = File(System.getProperty("user.dir"))
            .resolve("../../.scratch/puzzlebox/design/diagrams/matrix")
            .canonicalFile
    }
}

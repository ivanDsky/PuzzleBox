package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel

// ---------------------------------------------------------------------------
// Previews (A7/A8 — resting states only, registered for the T25 variant matrix).
// ---------------------------------------------------------------------------

/** Preview fixture: an n-sized board with the given waypoint cells and trace. */
internal fun zipPreviewModel(n: Int, waypoints: List<Int>, path: List<Int> = emptyList()): ZipRendererModel {
    val board = ZipBoard(n, emptySet(), waypoints.map(::Cell))
    val puzzle = ZipPuzzle(VariantId("zip/n$n"), board, null)
    return ZipRendererModel.of(ZipState(puzzle, path.map(::Cell)), null)
}

@PuzzleBoxPreviews
@Composable
internal fun ZipBoardDotsFreshPreview() {
    PuzzleBoxTheme {
        // n6 fresh: dots at the A6 proportion, hollow start ring, no trace.
        ZipBoard(zipPreviewModel(6, waypoints = listOf(0, 7, 20, 28, 35)), Modifier.padding(24.dp))
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ZipBoardDotsMidTracePreview() {
    PuzzleBoxTheme {
        // n6 mid-trace: fat token trace under the dots, halos on visited waypoints,
        // the traced start reading as a visited dot, waypoint 3 as the next target.
        ZipBoard(
            zipPreviewModel(
                6,
                waypoints = listOf(0, 5, 18, 30, 35),
                path = listOf(0, 6, 7, 8, 14, 15, 16, 17, 11, 5),
            ),
            Modifier.padding(24.dp),
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ZipBoardDotsN8FreshPreview() {
    PuzzleBoxTheme {
        // n8 with all 16 waypoints, two-digit labels "10"–"16" included; the last four
        // dots sit on adjacent cells — the no-merge stress case.
        ZipBoard(zipPreviewModel(8, waypoints = (0 until 16).map { it * 4 }), Modifier.padding(24.dp))
    }
}

/**
 * T25 gate visual (brief 22 §4): the U1-measured red→orange path gradient next to the
 * flat `pathTrace` token, same S-path. Preview-only swatch — production keeps the token;
 * the gradient hexes are U1's measured endpoints and ship only if the gate picks them.
 */
@PuzzleBoxPreviews
@Composable
internal fun ZipTraceGradientVsTokenPreview() {
    PuzzleBoxTheme {
        val board = LocalBoardColors.current
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("pathTrace token", style = MaterialTheme.typography.labelLarge)
            Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                drawSSample(SolidColor(board.pathTrace))
            }
            Spacer(Modifier.height(8.dp))
            Text("U1 gradient #EA3328→#F27B16", style = MaterialTheme.typography.labelLarge)
            Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                drawSSample(
                    Brush.linearGradient(listOf(Color(0xFFEA3328), Color(0xFFF27B16))),
                )
            }
        }
    }
}

private fun DrawScope.drawSSample(brush: Brush) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.1f, h * 0.2f)
        cubicTo(w * 0.4f, h * 0.2f, w * 0.4f, h * 0.8f, w * 0.5f, h * 0.8f)
        cubicTo(w * 0.6f, h * 0.8f, w * 0.6f, h * 0.2f, w * 0.9f, h * 0.2f)
    }
    drawPath(
        path,
        brush,
        style = Stroke(width = h * 0.35f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

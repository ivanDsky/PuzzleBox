package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.gameui.EdgeOverlay
import com.ivandsky.puzzlebox.gameui.EdgeSide
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.GameBoardRenderer
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.GridGeometry
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel

/**
 * Maps [ZipRendererModel] onto the shared game-ui [BoardGrid] (F2 §7): interior walls as
 * [EdgeOverlay.Wall]s, waypoint labels / start + hint highlights as cell content, the
 * player trace as a polyline over the path's cell centers with dots on its endpoints.
 * Pure render — every Zip decision stays in :games:zip (A16); this class paints the
 * model it is given, nothing more.
 */
class ZipBoardRenderer : GameBoardRenderer {

    @Composable
    override fun Content(model: RendererModel, modifier: Modifier) {
        val zip = model as? ZipRendererModel
            ?: error("ZipBoardRenderer expects ZipRendererModel, got ${model::class.simpleName}")
        // Zip boards are n x n: the wrapper owns the square (PuzzleBoard precedent), so
        // board and trace overlay share one coordinate space whatever the caller passes.
        Box(modifier.aspectRatio(1f)) {
            BoardGrid(
                rows = zip.n,
                cols = zip.n,
                modifier = Modifier.matchParentSize(),
                edgeOverlays = zip.walls.map { wall ->
                    EdgeOverlay.Wall(
                        cell = GridCell(row = wall.cellIdx / zip.n, col = wall.cellIdx % zip.n),
                        side = when (wall.direction) {
                            WallDirection.RIGHT -> EdgeSide.RIGHT
                            WallDirection.DOWN -> EdgeSide.DOWN
                        },
                    )
                },
                cellContent = { row, col -> Cell(zip, row, col) },
            )
            TracePath(zip, Modifier.matchParentSize())
        }
    }
}

@Composable
private fun Cell(model: ZipRendererModel, row: Int, col: Int) {
    val idx = row * model.n + col
    val render = model.cells.getOrNull(idx) ?: return
    val highlighted = idx == model.hintHighlight
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        render.numberLabel?.let { label ->
            Text(
                text = label.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (render.isStart) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** The committed trace: one line per path step, dots on both endpoints. */
@Composable
private fun TracePath(model: ZipRendererModel, modifier: Modifier) {
    val traceColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (model.path.isEmpty()) return@Canvas
        val geometry = GridGeometry(rows = model.n, cols = model.n, widthPx = size.width, heightPx = size.height)
        val points = model.path.map { idx ->
            geometry.centerOf(GridCell(row = idx / model.n, col = idx % model.n))
        }
        val stroke = Stroke(width = geometry.cellWidth * 0.18f)
        points.zipWithNext().forEach { (from, to) ->
            drawLine(traceColor, from, to, strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        listOf(points.first(), points.last()).forEach { center: Offset ->
            drawCircle(traceColor, radius = stroke.width, center = center)
        }
    }
}

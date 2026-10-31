package com.ivandsky.puzzlebox.play

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.EdgeOverlay
import com.ivandsky.puzzlebox.gameui.EdgeSide
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel

/**
 * The F2-1 Compose mapping, owned HERE (the state→model call site is the play feature;
 * game-ui never imports Zip): [ZipRendererModel] → the shared [BoardGrid]. Registered
 * into the [GameUiRegistry] by `playModule`; the interactive play screen reuses the same
 * wall overlays + cell decor inside a [com.ivandsky.puzzlebox.gameui.DragTraceBoard].
 */
fun GameUiRegistry.registerZipBoard() {
    register(ZipDefinition.id) { model, modifier ->
        ZipBoard(model as ZipRendererModel, modifier)
    }
}

/** Read-only board (no gesture layer): the registry-facing rendering of the model. */
@Composable
fun ZipBoard(model: ZipRendererModel, modifier: Modifier = Modifier) {
    BoardGrid(
        rows = model.n,
        cols = model.n,
        modifier = modifier,
        edgeOverlays = zipWallOverlays(model),
        cellContent = { row, col -> ZipCellDecor(model, row, col) },
    )
}

fun zipWallOverlays(model: ZipRendererModel): List<EdgeOverlay.Wall> =
    model.walls.map { wall ->
        EdgeOverlay.Wall(
            cell = GridCell(wall.cellIdx / model.n, wall.cellIdx % model.n),
            side = EdgeSide.valueOf(wall.direction.name),
        )
    }

private val cellCorner = RoundedCornerShape(10.dp)

/**
 * One cell of the trace: waypoint number, path dot + connectors toward consecutive path
 * neighbors, hint ring, and the transient rejection pulse (invalid drop feedback). All
 * sizes are fractions of the cell, so the decor scales from phone to tablet boards.
 */
@Composable
fun ZipCellDecor(model: ZipRendererModel, row: Int, col: Int, modifier: Modifier = Modifier) {
    val idx = row * model.n + col
    val cell = model.cells[idx]
    val pathIndex = model.path.indexOf(idx)
    val inPath = pathIndex >= 0
    val isHint = model.hintHighlight == idx
    val isRejected = model.rejection?.cellIdx == idx
    val scheme = MaterialTheme.colorScheme

    val pulse by animateFloatAsState(
        targetValue = if (isRejected) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "zipRejectionPulse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(if (isHint) "play.hint_cell" else "play.cell_$idx")
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isRejected) {
            Box(Modifier.fillMaxSize().background(scheme.errorContainer.copy(alpha = 0.7f), cellCorner))
        }
        if (inPath) {
            Connectors(model, pathIndex)
            Box(Modifier.fillMaxSize(0.68f).background(scheme.primary, androidx.compose.foundation.shape.CircleShape))
        }
        if (isHint) {
            Box(Modifier.fillMaxSize(0.9f).border(2.dp, scheme.tertiary, cellCorner))
        }
        cell.numberLabel?.let { label ->
            Text(
                text = label.toString(),
                style = if (cell.isStart) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                color = when {
                    inPath -> scheme.onPrimary
                    cell.isStart -> scheme.primary
                    else -> scheme.onSurfaceVariant
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.Connectors(model: ZipRendererModel, pathIndex: Int) {
    val scheme = MaterialTheme.colorScheme
    val after = model.path.getOrNull(pathIndex + 1)
    val before = model.path.getOrNull(pathIndex - 1)
    val here = model.path[pathIndex]
    for (neighbor in listOfNotNull(before, after)) {
        val delta = neighbor - here
        when {
            delta == 1 -> Box( // toward the right neighbor
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.24f)
                    .fillMaxWidth(0.5f)
                    .background(scheme.primary),
            )

            delta == -1 -> Box( // toward the left neighbor
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(0.24f)
                    .fillMaxWidth(0.5f)
                    .background(scheme.primary),
            )

            delta == model.n -> Box( // toward the cell below
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.24f)
                    .fillMaxHeight(0.5f)
                    .background(scheme.primary),
            )

            delta == -model.n -> Box( // toward the cell above
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.24f)
                    .fillMaxHeight(0.5f)
                    .background(scheme.primary),
            )
        }
    }
}

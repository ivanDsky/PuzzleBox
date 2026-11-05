package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.GridGeometry
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel

/**
 * Strands' one board, chrome-free ([com.ivandsky.puzzlebox.gameui.GamePlayUi.Board]): the
 * daily screen and the tutorial both draw it. Draws nothing until [controller] holds a
 * puzzle. Input here is taps only; each letter is its own button, so TalkBack and the JVM
 * tests reach it directly.
 */
@Composable
internal fun StrandsBoard(controller: StrandsPlayController, modifier: Modifier = Modifier) {
    val model = controller.model
    if (model.rows == 0) return
    StrandsBoardContent(model = model, onTap = controller::tap, modifier = modifier)
}

/** The stateless board: [model] drawn as round letter tokens over their connectors. */
@Composable
internal fun StrandsBoardContent(
    model: StrandsRendererModel,
    onTap: (Coord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    BoardGrid(
        rows = model.rows,
        cols = model.cols,
        modifier = modifier.testTag("strands.board"),
        // Strands has no grid: the letters float on the bare board.
        colors = colors.copy(boardGridLine = Color.Transparent),
        underlay = { StrandsConnectors(model, colors) },
        cellContent = { row, col ->
            LetterToken(model.cells[row * model.cols + col], colors, onTap)
        },
    )
}

@Composable
private fun StrandsConnectors(model: StrandsRendererModel, colors: BoardColors) {
    Canvas(Modifier.fillMaxSize()) {
        val geometry = GridGeometry(model.rows, model.cols, size.width, size.height)
        val stroke = Stroke(width = ConnectorWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (link in model.links) {
            if (link.cells.size < 2) continue
            val path = Path()
            link.cells.forEachIndexed { index, cell ->
                val center = geometry.centerOf(GridCell(cell.row, cell.col))
                if (index == 0) path.moveTo(center.x, center.y) else path.lineTo(center.x, center.y)
            }
            drawPath(path, color = connectorColor(link.kind, colors), style = stroke)
        }
    }
}

@Composable
private fun LetterToken(cell: StrandsCell, colors: BoardColors, onTap: (Coord) -> Unit) {
    val fill = tokenFill(cell, colors)
    val hinted = cell.state is CellState.HintOutlined || cell.state is CellState.HintOrderRevealed
    Box(
        Modifier
            .fillMaxSize()
            .testTag("strands.cell.${cell.coord.row}.${cell.coord.col}")
            .clickable(role = Role.Button) { onTap(cell.coord) }
            .semantics { contentDescription = cell.letter.toString() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize(TokenFraction)
                .drawBehind {
                    if (hinted) {
                        val width = HintRingWidth.toPx()
                        drawCircle(
                            color = colors.strandsHintRing,
                            radius = size.minDimension / 2 - width / 2,
                            style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 2, width * 1.5f))),
                        )
                    }
                }
                .clip(CircleShape)
                .background(fill ?: Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.letter.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = if (fill != null) colors.strandsTileText else colors.strandsIdleLetter,
            )
        }
    }
}

/** The live trace overlays any base state; null means the bare board shows through. */
private fun tokenFill(cell: StrandsCell, colors: BoardColors): Color? = when {
    cell.traceIndex != null -> colors.strandsSelectionFill
    cell.state == CellState.FoundSpangram -> colors.strandsSpangramFill
    cell.state == CellState.FoundTheme -> colors.strandsThemeFill
    else -> null
}

private fun connectorColor(kind: LinkKind, colors: BoardColors): Color = when (kind) {
    LinkKind.Trace -> colors.strandsTraceConnector
    LinkKind.Theme -> colors.strandsThemeConnector
    LinkKind.Spangram -> colors.strandsSpangramConnector
    LinkKind.Hint -> colors.strandsHintConnector
}

/** NYT draws 44 px tokens on a 54 px pitch; the connector is 12 px. */
private const val TokenFraction = 0.82f
private val ConnectorWidth = 12.dp
private val HintRingWidth = 3.dp

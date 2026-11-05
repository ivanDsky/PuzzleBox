package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.GridGeometry

/**
 * Every link as a round-capped polyline through its cell centres, drawn under the letters
 * (the board's underlay). The model orders links so the live trace lands on top.
 */
@Composable
internal fun StrandsConnectors(rows: Int, cols: Int, links: List<StrandsLink>, colors: BoardColors) {
    Canvas(Modifier.fillMaxSize()) {
        val geometry = GridGeometry(rows, cols, size.width, size.height)
        val path = Path()
        for (link in links) {
            if (link.cells.size < 2) continue
            path.reset()
            link.cells.forEachIndexed { index, cell ->
                val center = geometry.centerOf(GridCell(cell.row, cell.col))
                if (index == 0) path.moveTo(center.x, center.y) else path.lineTo(center.x, center.y)
            }
            val stroke = Stroke(width = connectorWidth(link.kind).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path, color = connectorColor(link.kind, colors), style = stroke)
        }
    }
}

internal fun connectorWidth(kind: LinkKind): Dp =
    if (kind == LinkKind.Spangram) StrandsBoardSpec.SpangramConnectorWidth else StrandsBoardSpec.ConnectorWidth

private fun connectorColor(kind: LinkKind, colors: BoardColors): Color = when (kind) {
    LinkKind.Trace -> colors.strandsTraceConnector
    LinkKind.Theme -> colors.strandsThemeConnector
    LinkKind.Spangram -> colors.strandsSpangramConnector
    LinkKind.Hint -> colors.strandsHintConnector
}

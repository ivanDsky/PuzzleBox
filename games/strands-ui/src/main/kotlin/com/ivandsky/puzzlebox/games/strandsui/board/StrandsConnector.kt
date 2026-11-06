package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 *
 * A hint link is the exception: its letters have no fill to hide it, so it runs only
 * from token edge to token edge, joining the dashed rings and never crossing a letter
 * (an idle letter keeps its >= 4.5:1 on the bare board, A32).
 */
@Composable
internal fun StrandsConnectors(rows: Int, cols: Int, links: List<StrandsLink>, colors: BoardColors) {
    Canvas(Modifier.fillMaxSize()) {
        val geometry = GridGeometry(rows, cols, size.width, size.height)
        val tokenRadius = minOf(geometry.cellWidth, geometry.cellHeight) * StrandsBoardSpec.TokenFraction / 2
        val path = Path()
        for (link in links) {
            if (link.cells.size < 2) continue
            val color = connectorColor(link.kind, colors)
            val width = connectorWidth(link.kind).toPx()
            val centers = link.cells.map { geometry.centerOf(GridCell(it.row, it.col)) }
            if (link.kind == LinkKind.Hint) {
                for ((from, to) in hintSegments(centers, tokenRadius)) {
                    drawLine(color, from, to, strokeWidth = width, cap = StrokeCap.Butt)
                }
                continue
            }
            path.reset()
            centers.forEachIndexed { index, center ->
                if (index == 0) path.moveTo(center.x, center.y) else path.lineTo(center.x, center.y)
            }
            drawPath(path, color = color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * The hint link's visible pieces: each step between neighbouring [centers], trimmed by
 * [tokenRadius] at both ends. Butt-capped, so nothing reaches inside a token.
 */
internal fun hintSegments(centers: List<Offset>, tokenRadius: Float): List<Pair<Offset, Offset>> =
    centers.zipWithNext { from, to ->
        val step = to - from
        val inset = step * (tokenRadius / step.getDistance())
        from + inset to to - inset
    }

internal fun connectorWidth(kind: LinkKind): Dp =
    if (kind == LinkKind.Spangram) StrandsBoardSpec.SpangramConnectorWidth else StrandsBoardSpec.ConnectorWidth

private fun connectorColor(kind: LinkKind, colors: BoardColors): Color = when (kind) {
    LinkKind.Trace -> colors.strandsTraceConnector
    LinkKind.Theme -> colors.strandsThemeConnector
    LinkKind.Spangram -> colors.strandsSpangramConnector
    LinkKind.Hint -> colors.strandsHintConnector
}

package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.GridGeometry

/**
 * Every link as a round-capped polyline through its cell centres, drawn under the letters
 * (the board's underlay): the board's [links], then the live [trace] on top.
 *
 * A hint link is the exception: its letters have no fill to hide it, so it runs only
 * from token edge to token edge, joining the dashed rings and never crossing a letter
 * (an idle letter keeps its >= 4.5:1 on the bare board, A32).
 *
 * The live trace is [StrandsLineMotion]'s: while a drag runs, [finger] (read in the draw
 * phase) stretches its end toward a free letter (A4), and [animate] turns on grow-in,
 * retract, tip-home and the head pop; reduced motion keeps only the tip. With [snap] (flag
 * SnapTrace) the live trace is [StrandsSnapLine] instead: no finger, tip or pop, the end on
 * the last letter centre; [finger] is ignored. [links] are built in a draw cache, rebuilt
 * only on a size or link change.
 */
@Composable
internal fun StrandsConnectors(
    rows: Int,
    cols: Int,
    links: List<StrandsLink>,
    trace: () -> Trace,
    colors: BoardColors,
    finger: () -> TraceFinger? = { null },
    animate: Boolean = false,
    snap: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val motion = remember { StrandsLineMotion(scope) }
    val snapLine = if (snap) remember { StrandsSnapLine(scope) } else null
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val geometry = GridGeometry(rows, cols, size.width, size.height)
                val alt = altThemeLinks(links)
                val drawn = links.filter { it.cells.size >= 2 }.map { linkDrawing(it, geometry, colors, it in alt) }
                onDrawBehind { for (i in drawn.indices) drawn[i](this) }
            }
            .drawWithCache {
                val color = connectorColor(LinkKind.Trace, colors)
                val stroke = roundStroke(LinkKind.Trace)
                onDrawBehind {
                    if (snapLine != null) {
                        with(snapLine) { drawTrace(trace().path, rows, cols, color, stroke, animate) }
                    } else {
                        with(motion) { drawTrace(trace().path, finger(), rows, cols, color, stroke, animate) }
                    }
                }
            },
    )
}

/** One link's draw call over geometry built once: a stroked polyline, or a hint's trimmed butt-capped pieces. */
private fun CacheDrawScope.linkDrawing(link: StrandsLink, geometry: GridGeometry, colors: BoardColors, alt: Boolean): DrawScope.() -> Unit {
    val color = if (alt) colors.strandsThemeAltConnector else connectorColor(link.kind, colors)
    if (link.kind != LinkKind.Hint) {
        val path = Path().polylineThrough(link.cells, geometry)
        val stroke = roundStroke(link.kind)
        return { drawPath(path, color, style = stroke) }
    }
    val tokenRadius = minOf(geometry.cellWidth, geometry.cellHeight) * StrandsBoardSpec.TokenFraction / 2
    val pieces = hintSegments(link.cells.map { geometry.centerOf(GridCell(it.row, it.col)) }, tokenRadius)
    val width = connectorWidth(link.kind).toPx()
    return {
        for (i in pieces.indices) drawLine(color, pieces[i].first, pieces[i].second, strokeWidth = width, cap = StrokeCap.Butt)
    }
}

private fun Path.polylineThrough(cells: List<Coord>, geometry: GridGeometry): Path = apply {
    cells.forEachIndexed { index, cell ->
        val center = geometry.centerOf(GridCell(cell.row, cell.col))
        if (index == 0) moveTo(center.x, center.y) else lineTo(center.x, center.y)
    }
}

private fun CacheDrawScope.roundStroke(kind: LinkKind): Stroke =
    Stroke(width = connectorWidth(kind).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

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

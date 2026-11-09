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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
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
 * The live trace is [StrandsLineMotion]'s: while a drag runs, [finger] (cell units, read in
 * the draw phase) leans its end along one legal direction from the last letter — never at a
 * [found] letter — and [animate] turns on grow-in, retract and the tip's return home; reduced
 * motion keeps only the tip. [links] are built in a draw cache, rebuilt only on a size or
 * link change. The [settle]ing link, a word's own while its letters bounce, is drawn
 * from their frames instead ([LinkSettle]): it never stays thick behind a tile that has shrunk.
 */
@Composable
internal fun StrandsConnectors(
    rows: Int,
    cols: Int,
    links: List<StrandsLink>,
    trace: () -> Trace,
    colors: BoardColors,
    finger: () -> Offset? = { null },
    found: (cell: Coord) -> Boolean = { false },
    animate: Boolean = false,
    settle: () -> LinkSettle?,
) {
    val scope = rememberCoroutineScope()
    val motion = remember { StrandsLineMotion(scope) }
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val geometry = GridGeometry(rows, cols, size.width, size.height)
                val alt = altThemeLinks(links)
                val visible = links.filter { it.cells.size >= 2 }
                val drawn = visible.map { linkDrawing(it, geometry, colors, it in alt) }
                onDrawBehind {
                    val settling = settle()
                    for (i in drawn.indices) {
                        if (settling != null && visible[i] == settling.link) {
                            val color = linkColor(visible[i], colors, visible[i] in alt)
                            drawSettling(settling, geometry, color, connectorColor(LinkKind.Trace, colors))
                        } else {
                            drawn[i](this)
                        }
                    }
                }
            }
            .drawWithCache {
                val color = connectorColor(LinkKind.Trace, colors)
                val stroke = roundStroke(LinkKind.Trace)
                onDrawBehind {
                    with(motion) { drawTrace(trace().path, finger(), found, rows, cols, color, stroke, animate) }
                }
            },
    )
}

/** One link's draw call over geometry built once: a stroked polyline, or a hint's trimmed butt-capped pieces. */
private fun CacheDrawScope.linkDrawing(link: StrandsLink, geometry: GridGeometry, colors: BoardColors, alt: Boolean): DrawScope.() -> Unit {
    val color = linkColor(link, colors, alt)
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

private fun linkColor(link: StrandsLink, colors: BoardColors, alt: Boolean): Color =
    if (alt) colors.strandsThemeAltConnector else connectorColor(link.kind, colors)

/**
 * A settling link's steps, round-capped like the link at rest. Until its letters light a step
 * is the trace line the player just drew ([LinkKind.Trace]'s colour and width); it turns into
 * the link's own with them, and is as thick as the thinner of its two tiles is scaled.
 */
private fun DrawScope.drawSettling(
    settle: LinkSettle,
    geometry: GridGeometry,
    color: Color,
    traceColor: Color,
) {
    val scales = settle.stepScales()
    val lit = settle.stepLit()
    val traceWidth = connectorWidth(LinkKind.Trace).toPx()
    val restWidth = connectorWidth(settle.link.kind).toPx()
    val cells = settle.link.cells
    for (i in scales.indices) {
        val from = geometry.centerOf(GridCell(cells[i].row, cells[i].col))
        val to = geometry.centerOf(GridCell(cells[i + 1].row, cells[i + 1].col))
        val width = lerp(traceWidth, restWidth, lit[i]) * scales[i]
        drawLine(lerp(traceColor, color, lit[i]), from, to, strokeWidth = width, cap = StrokeCap.Round)
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

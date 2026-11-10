package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.ceil

// Pure geometry of one Spotlight over a uniform rows × cols grid that fills the overlay's bounds.

/** One unit segment of the cell lattice, between two grid corners given as (column, row). */
internal data class GridEdge(val col0: Int, val row0: Int, val col1: Int, val row1: Int)

/** Which side of its anchor the callout sits on. */
internal enum class CalloutSide {
    /** Above the anchor; the pointer points down at the anchor's top edge. */
    ABOVE,

    /** Below the anchor; the pointer points up at the anchor's bottom edge. */
    BELOW,

    /** No band beside the anchor fits: the bubble sits over the board with no pointer. */
    OVER,
}

/** The callout's [top] (bubble and pointer together), its [side], and the x its pointer aims at. */
internal data class CalloutPlacement(val top: Float, val side: CalloutSide, val anchorX: Float)

private val Spotlight.cellCount: Int get() = if (rows > 0 && cols > 0) rows * cols else 0

/** The listed cells that lie on the board; empty means the overlay draws nothing. */
internal fun Spotlight.litCells(): Set<Int> = roles.keys.filterTo(LinkedHashSet()) { it in 0 until cellCount }

/** Cell [cell]'s rect: column `cell % cols`, row `cell / cols`, each `width / cols` by `height / rows`. */
internal fun cellRect(cell: Int, rows: Int, cols: Int, size: Size): Rect {
    val w = size.width / cols
    val h = size.height / rows
    val col = cell % cols
    val row = cell / cols
    return Rect(col * w, row * h, (col + 1) * w, (row + 1) * h)
}

/** The outer boundary of [lit] (see [litCells]) as unit edges; none on a culprit step (A14). */
internal fun Spotlight.hairlineEdges(lit: Set<Int>): List<GridEdge> {
    if (lit.any { roles[it] == SpotlightRole.CULPRIT }) return emptyList()
    fun isLit(row: Int, col: Int) = row in 0 until rows && col in 0 until cols && row * cols + col in lit
    return buildList {
        for (cell in lit.sorted()) {
            val row = cell / cols
            val col = cell % cols
            if (!isLit(row - 1, col)) add(GridEdge(col, row, col + 1, row))
            if (!isLit(row + 1, col)) add(GridEdge(col, row + 1, col + 1, row + 1))
            if (!isLit(row, col - 1)) add(GridEdge(col, row, col, row + 1))
            if (!isLit(row, col + 1)) add(GridEdge(col + 1, row, col + 1, row + 1))
        }
    }
}

/**
 * Places a full-width callout [height] tall (bubble plus pointer) on a board of [size]: in the free row band nearest
 * the anchor, hugging it. With no free band (a small board mostly lit), the band covering the fewest lit cells, with
 * evidence, target, culprit and anchor cells weighted far ahead of excluded ones (prototype 07 `placeCallout`).
 * Null when there is no callout or nothing is lit.
 */
internal fun Spotlight.calloutPlacement(lit: Set<Int>, size: Size, height: Float): CalloutPlacement? {
    val callout = callout ?: return null
    if (lit.isEmpty()) return null
    val cellH = size.height / rows
    val anchor = callout.anchor.filter { it in 0 until cellCount }.ifEmpty { lit.toList() }
    val anchorTop = anchor.minOf { it / cols }
    val anchorBottom = anchor.maxOf { it / cols }
    val anchorX = (anchor.minOf { it % cols } + anchor.maxOf { it % cols } + 1) / 2f * (size.width / cols)
    // Prototype `ceil(h / p - 0.1)`: a bubble a hair over a row still takes one row.
    val span = ceil(height / cellH - 0.1f).toInt().coerceAtLeast(1)
    if (span > rows) return CalloutPlacement(0f, CalloutSide.OVER, anchorX)

    fun sideOf(band: Int) = when {
        band + span <= anchorTop -> CalloutSide.ABOVE
        band > anchorBottom -> CalloutSide.BELOW
        else -> CalloutSide.OVER
    }
    val aboveFirst = anchorTop >= rows - 1 - anchorBottom
    val nearest = compareBy<Int>(
        { band ->
            when (sideOf(band)) {
                CalloutSide.ABOVE -> anchorTop - (band + span)
                CalloutSide.BELOW -> band - anchorBottom - 1
                CalloutSide.OVER -> rows
            }
        },
        { band -> if ((sideOf(band) == CalloutSide.ABOVE) == aboveFirst) 0 else 1 },
    )
    fun covered(band: Int) = lit.filter { it / cols in band until band + span }
    fun weight(cell: Int) = if (roles[cell] == SpotlightRole.EXCLUDED && cell !in anchor) 1 else CRITICAL_WEIGHT
    val bands = (0..rows - span).toList()
    val band = bands.filter { covered(it).isEmpty() }.minWithOrNull(nearest)
        ?: bands.minWith(compareBy<Int> { b -> covered(b).sumOf(::weight) }.then(nearest))
    val side = sideOf(band)
    val top = if (side == CalloutSide.ABOVE) (band + span) * cellH - height else band * cellH
    return CalloutPlacement(top.coerceIn(0f, (size.height - height).coerceAtLeast(0f)), side, anchorX)
}

/** An evidence, target, culprit or anchor cell outweighs the excluded cells a fallback band could cover instead. */
private const val CRITICAL_WEIGHT = 100

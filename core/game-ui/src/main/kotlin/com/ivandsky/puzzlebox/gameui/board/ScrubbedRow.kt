package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.ScrubCell

// What a place cell says, apart from the row's other cells: two rows with one label have the same place cell.
private data class PlaceLabel(val place: Int?, val beyondPage: Boolean, val medal: Boolean)

private val RowCells.label get() = PlaceLabel(place, beyondPage, placeMedal)

// Rows that overlap are drawn on what they sit on (see LocalBoardBackdrop), so a tint is laid over it, not left see-through.
private fun fillOf(base: Color?, medal: MedalRow?): Color? =
    if (medal == null) base else base?.let { medal.tint.compositeOver(it) } ?: medal.tint

// How far down the strip the roll is when the row is this far between its two boards.
private fun RankStrip.offsetAt(progress: Float): Float = lerp(start.toFloat(), end.toFloat(), progress)

/**
 * The row's fill in [shape]: what it sits on ([base]), under the tint of the medal its place earns. A row between
 * two boards ([progress] not null) passes through the tints of the places its place cell rolls past, and reads
 * [progress] only when the row is drawn.
 */
internal fun Modifier.rowFill(base: Color?, was: RowCells, cells: RowCells, progress: (() -> Float)?, shape: Shape): Modifier {
    val fill = fillOf(base, cells.medal)
    if (progress == null || was.medal == null && cells.medal == null) return if (fill != null) background(fill, shape) else this
    val from = was.place
    val to = cells.place
    val strip = if (was.counted && cells.counted && was.medalRow && cells.medalRow && from != null && to != null) rankStrip(from, to) else null
    // A row with nothing under it and no medal has no fill: the tint fades to and from nothing of its own colour.
    val none = (cells.medal ?: was.medal)?.tint?.copy(alpha = 0f)
    val fills = (strip?.cells?.map { fillOf(base, medalRowOf(it)) } ?: listOf(fillOf(base, was.medal), fill)).map { it ?: none }
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            val at = (strip?.offsetAt(progress()) ?: progress()).coerceIn(0f, fills.lastIndex.toFloat())
            val low = at.toInt()
            val high = (low + 1).coerceAtMost(fills.lastIndex)
            val under = fills[low]
            val over = fills[high]
            if (under != null && over != null) drawOutline(outline, lerp(under, over, at - low))
        }
    }
}

/** The row's minimum height, [to]; between two boards whose heights differ ([progress] not null) it grows from [from] with the way, read only at layout. */
internal fun Modifier.rowMinHeight(from: Dp, to: Dp, progress: (() -> Float)?): Modifier =
    if (progress == null || from == to) {
        defaultMinSize(minHeight = to)
    } else {
        layout { measurable, constraints ->
            val min = lerp(from, to, progress().coerceIn(0f, 1f)).roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
            val placeable = measurable.measure(constraints.copy(minHeight = min))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }

/**
 * The place cell of a row between two boards: the numbers from the place it [was] at to the one in [cells] roll past
 * as an odometer, medals among them, with [progress] and not on a clock. A place that is no number on either board
 * (hidden, "100+") frosts over instead, and one that is the same on both does nothing.
 */
@Composable
internal fun ScrubbedPlace(was: RowCells, cells: RowCells, quiet: Color, tag: String, progress: () -> Float) {
    val from = was.place
    val to = cells.place
    val rolls = was.label != cells.label && LocalPlaceMotion.current != null && was.counted && cells.counted && was.placeMedal == cells.placeMedal
    if (rolls && from != null && to != null) {
        val strip = remember(from, to) { rankStrip(from, to) }
        Box(Modifier.size(RankCellSize).clipToBounds().placeSemantics(tag, to)) {
            RankStripColumn(strip, quiet, cells.placeMedal, { strip.offsetAt(progress()) })
        }
    } else {
        ScrubCell(was.label, cells.label, progress) { RankPlace(it.place, quiet, tag, it.beyondPage, it.medal) }
    }
}

/** One cell of a row: [content] of [value], frosting over from what it [was] on the board the row leaves while there is a [scrub]. */
@Composable
internal fun <T> RowCell(
    was: T,
    value: T,
    scrub: RowScrub?,
    alignment: Alignment = Alignment.CenterStart,
    content: @Composable (T) -> Unit,
) {
    if (scrub == null || was == value) content(value) else ScrubCell(was, value, scrub.progress, alignment = alignment, content = content)
}

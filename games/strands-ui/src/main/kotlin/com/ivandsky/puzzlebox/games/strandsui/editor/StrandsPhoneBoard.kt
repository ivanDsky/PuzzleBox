package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strandsui.board.strandsCellSizeFor

/**
 * The phone Studio's board: the [grid] over the word [field] over the [rest] of the tools,
 * which scroll in what is left, all sized in the measure pass from the slot it is given. The
 * field shows whole, its supporting line too, once the slot is three fields tall; from two to
 * three it slides in under the grid, which holds two fields' height meanwhile, and fades in over
 * the second half, so a slot that stops part-way never shows a cut field; below two the grid has
 * the slot alone (the publish details). So the grid follows the slot without a step.
 *
 * The grid draws the play board, which takes its cell size in composition. Under a lookahead
 * (the app's shared transition layout) that composition is the slot's target size, while an
 * animating slot gives the approach pass its size frame by frame: the grid is measured at the
 * target and scaled to the frame's height, so it shrinks and grows with the slot.
 */
@Composable
internal fun StrandsPhoneBoard(
    grid: @Composable () -> Unit,
    field: @Composable () -> Unit,
    rest: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The grid height the last lookahead pass measured; the approach passes after it scale to theirs.
    val ahead = remember { AheadHeight() }
    Layout(listOf(grid, field, rest), modifier.clipToBounds()) { (gridSlot, fieldSlot, restSlot), constraints ->
        val width = constraints.maxWidth
        val bounded = constraints.hasBoundedHeight
        val fieldPlaceable = fieldSlot.single().measure(Constraints(maxWidth = width))
        val fieldHeight = fieldPlaceable.height
        val peek = if (bounded) (constraints.maxHeight - 2 * fieldHeight).coerceIn(0, fieldHeight) else fieldHeight
        val budget = if (bounded) (constraints.maxHeight - peek).toDp() else Dp.Infinity
        val cell = strandsCellSizeFor(width.toDp(), budget, StrandsDraft.ROWS, StrandsDraft.COLS, floor = 0.dp)
        val gridHeight = (cell * StrandsDraft.ROWS).roundToPx()
        if (isLookingAhead) ahead.height = gridHeight
        val drawn = if (isLookingAhead || ahead.height <= 0) gridHeight else ahead.height
        val gridPlaceable = gridSlot.single().measure(Constraints.fixed(width, drawn))
        val restMeasurable = restSlot.single()
        val restMax = if (bounded) (constraints.maxHeight - gridHeight - fieldHeight).coerceAtLeast(0) else restMeasurable.maxIntrinsicHeight(width)
        val restPlaceable = restMeasurable.measure(Constraints(maxWidth = width, maxHeight = restMax))
        val tools = if (peek > 0) fieldHeight + restPlaceable.height else 0
        val height = (gridHeight + tools).coerceAtMost(constraints.maxHeight).coerceAtLeast(constraints.minHeight)
        val scale = if (drawn > 0) gridHeight / drawn.toFloat() else 1f
        val shown = if (fieldHeight > 0) ((2f * peek - fieldHeight) / fieldHeight).coerceIn(0f, 1f) else 0f
        layout(width, height) {
            gridPlaceable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            if (shown > 0f) {
                fieldPlaceable.placeWithLayer(0, gridHeight) { alpha = shown }
                restPlaceable.placeWithLayer(0, gridHeight + fieldHeight) { alpha = shown }
            }
        }
    }
}

/** Written by the lookahead pass, read by the approach passes after it: never a snapshot state. */
private class AheadHeight {
    var height: Int = 0
}

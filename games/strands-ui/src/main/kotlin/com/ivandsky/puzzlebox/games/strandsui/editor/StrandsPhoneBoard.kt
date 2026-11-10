package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strandsui.board.strandsCellSizeFor
import kotlin.math.roundToInt

/**
 * The phone Studio's board: the [grid] over the word [field], both sized in the measure pass
 * from the slot it is given. A settled slot at least two fields tall shows the field whole, its
 * supporting line too, under a grid that takes the rest; a shorter one (the publish details)
 * gives the grid the slot alone. The paths list stays in the tablet's side pane: on the phone a
 * tap on the board selects a path, and the tool hint names it and holds its spangram toggle.
 *
 * The grid draws the play board, which takes its cell size in composition. Under a lookahead
 * (the app's shared transition layout) that composition is the slot's target size, while an
 * animating slot gives the approach pass its size frame by frame: the grid is measured at the
 * target and scaled, frame by frame, from the layout shown when the target changed to the
 * target's, in step with the slot, and the field fades in or out with it. A slot only settles
 * at its target, so a settled board never shows a cut or faded field.
 */
@Composable
internal fun StrandsPhoneBoard(
    grid: @Composable () -> Unit,
    field: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swap = remember { Swap() }
    Layout(listOf(grid, field), modifier.clipToBounds()) { (gridSlot, fieldSlot), constraints ->
        val width = constraints.maxWidth
        val fieldPlaceable = fieldSlot.single().measure(Constraints(maxWidth = width))
        val slot = constraints.maxHeight
        val settled = settledAt(slot, width, fieldPlaceable.height)
        if (isLookingAhead) swap.aim(settled)
        val frame = if (isLookingAhead) settled else swap.at(slot) ?: settled
        // Measured at the target in both passes, so the grid composes once; the frame scales it.
        val drawn = swap.target?.grid ?: frame.grid
        val gridPlaceable = gridSlot.single().measure(Constraints.fixed(width, drawn))
        val scale = if (drawn > 0) frame.grid / drawn.toFloat() else 1f
        val tools = if (frame.field > 0f) fieldPlaceable.height else 0
        val height = (frame.grid + tools).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            gridPlaceable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            if (frame.field > 0f) fieldPlaceable.placeWithLayer(0, frame.grid) { alpha = frame.field }
        }
    }
}

/** The board settled in a [slot] px tall ([Constraints.Infinity] when unbounded): the field under the grid from two fields up. */
private fun Density.settledAt(slot: Int, width: Int, fieldHeight: Int): BoardFrame {
    val withField = slot >= 2 * fieldHeight
    val budget = if (slot == Constraints.Infinity) Dp.Infinity else (slot - if (withField) fieldHeight else 0).toDp()
    val cell = strandsCellSizeFor(width.toDp(), budget, StrandsDraft.ROWS, StrandsDraft.COLS, floor = 0.dp)
    return BoardFrame(slot, (cell * StrandsDraft.ROWS).roundToPx(), if (withField) 1f else 0f)
}

/** A [slot] height, the [grid] height drawn in it and the [field]'s alpha (0: not placed). */
private data class BoardFrame(val slot: Int, val grid: Int, val field: Float)

/**
 * The lookahead's settled board and the frame shown when it last changed. Written in measure,
 * never snapshot state: the lookahead pass runs before the approach passes it sets up.
 */
private class Swap {
    var target: BoardFrame? = null
        private set
    private var from: BoardFrame? = null
    private var last: BoardFrame? = null

    fun aim(next: BoardFrame) {
        if (next == target) return
        from = last ?: next
        target = next
    }

    /** The frame for an approach [slot]: [from] to [target] in step with the slot; null without a lookahead. */
    fun at(slot: Int): BoardFrame? {
        val to = target ?: return null
        val start = from ?: to
        val span = to.slot.toLong() - start.slot
        val bounded = to.slot != Constraints.Infinity && start.slot != Constraints.Infinity
        val progress = if (span == 0L || !bounded) 1f else ((slot.toLong() - start.slot).toFloat() / span).coerceIn(0f, 1f)
        val grid = (start.grid + (to.grid - start.grid) * progress).roundToInt().coerceAtMost(slot)
        return BoardFrame(slot, grid, start.field + (to.field - start.field) * progress).also { last = it }
    }
}

package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightOverlay
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.GridGeometry

/** True when system animations are off — every board entrance/exit collapses to an
 *  instant state change (direction doc §5 reduced-motion rule). Read at board level
 *  from the shared [rememberReducedMotion] and provided down. */
val LocalQueensReducedMotion = staticCompositionLocalOf { false }

/** The Spotlight layer's modifier; built once, the overlay sizes itself to the board box (the cell grid). */
private val SpotlightOnBoard = Modifier.testTag("queens.spotlight")

/**
 * The full Queens board UI (queens-08): provenance-distinct X marks (§3.2/§3.3), spec
 * A13's satisfaction motion ([rememberQueensMotion] — Pop, Stagger, Pulse, Shake, the
 * solve orchestration), the ALWAYS-ON A14 violation stripes (fix round 6), the §5
 * interaction set on [QueensBoardState], and the Spotlight hint ([SpotlightOverlay] over the
 * cells). The win presentation is NOT here: the solved session hands off to the opaque result
 * ROUTE at screen level (fix round 6 — Zip's ResultScreen presentation; the modal overlay is
 * deleted), so the board never renders win content. Gesture surface is board-level with
 * edge-clamped cell resolution (§6); all game decisions stay in the session/state holder.
 */
@Composable
fun QueensPlayBoard(
    state: QueensBoardState,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    CompositionLocalProvider(
        LocalQueensReducedMotion provides reducedMotion,
        LocalQueensMotion provides rememberQueensMotion(state),
    ) {
        QueensPlayBoardContent(state, modifier)
    }
}

/** A tap's haptic against the session [before] it: a queen placed or removed buzzes
 *  heavy (§5.2), a × placed ticks light, a tap that changed nothing stays silent. */
internal fun QueensBoardState.tapHaptic(before: QueensSession): HapticFeedbackType? = when {
    session === before -> null
    lastPlacedQueen != null || lastRemovedQueen != null -> HapticFeedbackType.LongPress
    else -> HapticFeedbackType.TextHandleMove
}

@Composable
private fun QueensPlayBoardContent(
    state: QueensBoardState,
    modifier: Modifier,
) {
    val model = QueensBoardModel.of(state.session, state.guide)
    val haptics = LocalHapticFeedback.current
    var pressedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tracedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var downCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun cellOf(x: Float, y: Float, sidePx: Float): Pair<Int, Int> {
        // Edge-clamped resolution: a drag leaving the board ends on a boundary cell.
        val col = (x / sidePx * model.n).toInt().coerceIn(0, model.n - 1)
        val row = (y / sidePx * model.n).toInt().coerceIn(0, model.n - 1)
        return row to col
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            // Keyed on the state too: the next lesson or a replay brings a new state of
            // the same size, and the gestures must feed it, not the solved one.
            .pointerInput(state, model.n) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        pressedCell = null
                    },
                    onTap = { offset ->
                        val (row, col) = cellOf(offset.x, offset.y, size.width.toFloat())
                        pressedCell = null
                        val before = state.session
                        state.onTap(row * model.n + col)
                        state.tapHaptic(before)?.let(haptics::performHapticFeedback)
                    },
                )
            }
            .pointerInput(state, model.n) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    downCell = cellOf(down.position.x, down.position.y, size.width.toFloat())
                    pressedCell = downCell
                }
            }
            .pointerInput(state, model.n) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val cell = downCell ?: cellOf(offset.x, offset.y, size.width.toFloat())
                        tracedCell = cell
                        pressedCell = cell
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        state.onDragStart(cell.first * model.n + cell.second)
                    },
                    onDrag = { change, _ ->
                        val cell = cellOf(change.position.x, change.position.y, size.width.toFloat())
                        if (cell != tracedCell) {
                            tracedCell = cell
                            pressedCell = cell
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            state.onDragCell(cell.first * model.n + cell.second)
                        }
                    },
                    onDragEnd = {
                        state.onDragEnd()
                        tracedCell = null
                        pressedCell = null
                    },
                    onDragCancel = {
                        state.onDragEnd()
                        tracedCell = null
                        pressedCell = null
                    },
                )
            },
    ) {
        BoardGrid(
            rows = model.n,
            cols = model.n,
            modifier = Modifier.matchParentSize(),
            cellContent = { row, col ->
                QueensCell(
                    model = model,
                    idx = row * model.n + col,
                    staggerFrom = state.lastPlacedQueen,
                )
            },
        )
        // §3.1 cell-edge layer (F-1): the region fills are opaque and sit ABOVE
        // BoardGrid's under-strokes, so cell boundaries must be drawn over the fills —
        // a hairline lattice on every cell edge and heavier ink on region boundaries.
        // Glyphs never reach cell edges, so the marks stay visually above this layer.
        val edgeInk = MaterialTheme.colorScheme.onSurface
        Canvas(Modifier.matchParentSize()) {
            drawCellEdges(model, edgeInk)
        }
        // §3.1: instant pressed-cell highlight — touch-down feedback precedes commit.
        val pressHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        pressedCell?.let { (row, col) ->
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val g = GridGeometry(model.n, model.n, size.width, size.height)
                        drawRect(
                            pressHighlight,
                            topLeft = Offset(col * g.cellWidth, row * g.cellHeight),
                            size = Size(g.cellWidth, g.cellHeight),
                        )
                    },
            )
        }
        // The Stepwise hint (28) over everything above, so no line or highlight crosses its callout; it takes no
        // taps, so the board's gestures still get them. Null composes nothing.
        SpotlightOverlay(state.spotlight, SpotlightOnBoard)
    }
}

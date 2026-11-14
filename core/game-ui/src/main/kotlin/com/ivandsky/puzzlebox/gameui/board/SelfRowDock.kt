package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

// The dock appears once under a third of the row is in view and leaves once three quarters are: no flicker at the edge.
private const val DockBelow = 0.35f
private const val UndockAbove = 0.75f

/** The edge of the viewport the viewer's row left through, and so the one it docks to. */
internal enum class DockEdge { Top, Bottom }

/**
 * Whether the row at [row] is docked after a scroll, given the [visible] part of the board, both in the board's space:
 * the edge it left through while it is mostly out of view, null while it is in view. [current] is the edge it docks to
 * now, which is what gives the dock its two thresholds.
 */
internal fun nextDockEdge(current: DockEdge?, row: Rect, visible: Rect): DockEdge? {
    if (visible.isEmpty || row.height <= 0f) return null
    val edge = when {
        row.top < visible.top -> DockEdge.Top
        row.bottom > visible.bottom -> DockEdge.Bottom
        else -> return null
    }
    val shown = (minOf(row.bottom, visible.bottom) - maxOf(row.top, visible.top)).coerceIn(0f, row.height) / row.height
    return when {
        current == null -> edge.takeIf { shown < DockBelow }
        shown > UndockAbove -> null
        else -> edge
    }
}

/**
 * Where the viewer's own row is on the board that scrolls under a viewport, and the edge it is docked to. The board
 * and the row report where they are on every layout, the row is placed by [dockedRow], and the dock reads [edge] and
 * where the viewport's edges fall on the board. [requester] brings the row back into view.
 */
@Stable
internal class SelfRowDock {
    val requester = BringIntoViewRequester()

    /** The edge the row is docked to, null while it is in view. */
    var edge by mutableStateOf<DockEdge?>(null)
        private set

    /** The last edge it was docked to, so a dock that is leaving stays where it was. */
    var side by mutableStateOf(DockEdge.Top)
        private set
    var visibleTop by mutableFloatStateOf(0f)
        private set
    var visibleBottom by mutableFloatStateOf(0f)
        private set
    private var board: LayoutCoordinates? = null
    private var row: LayoutCoordinates? = null

    fun boardPlaced(coordinates: LayoutCoordinates) {
        board = coordinates
        refresh()
    }

    fun rowPlaced(coordinates: LayoutCoordinates) {
        row = coordinates
        refresh()
    }

    // The part of the board in view is the board's window bounds, which the scroll container's viewport clips; the row
    // is read unclipped, because what matters is how far out of view it is.
    private fun refresh() {
        val board = board?.takeIf { it.isAttached } ?: return
        val row = row?.takeIf { it.isAttached } ?: return
        val inWindow = board.boundsInWindow()
        val visible = if (inWindow.isEmpty) Rect.Zero else Rect(board.windowToLocal(inWindow.topLeft), board.windowToLocal(inWindow.bottomRight))
        val next = nextDockEdge(edge, board.localBoundingBoxOf(row, clipBounds = false), visible)
        visibleTop = visible.top
        visibleBottom = visible.bottom
        if (next != null) side = next
        if (next != edge) edge = next
    }
}

/** The dock of the board being composed; null on a board whose own row does not dock. */
internal val LocalSelfRowDock = staticCompositionLocalOf<SelfRowDock?> { null }

/** The viewer's row on a board that docks: reports where it is, and jumps back into view when the dock is tapped. */
internal fun Modifier.dockedRow(dock: SelfRowDock?): Modifier =
    if (dock == null) this else bringIntoViewRequester(dock.requester).onGloballyPositioned { dock.rowPlaced(it) }

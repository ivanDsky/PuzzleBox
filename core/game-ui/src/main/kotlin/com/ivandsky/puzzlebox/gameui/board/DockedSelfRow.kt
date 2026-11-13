package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.RankOrder
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

/**
 * [board] with the viewer's [self] row docked to the top or bottom edge of the viewport while it is scrolled out of
 * view, when [enabled]: a copy of the row laid over the board at that edge, which jumps to the row when tapped. The
 * board is drawn exactly as it is without a dock, and the row in it is the one at its own place, so the dock never
 * changes what the board animates. [board] gets the modifier the board is to carry.
 */
@Composable
internal fun DockedSelfRow(
    enabled: Boolean,
    self: DailyBoardLine.Real?,
    order: RankOrder,
    withPuzzleBoxRank: Boolean,
    medals: Boolean,
    animated: Boolean,
    backdrop: Color,
    modifier: Modifier,
    board: @Composable (Modifier) -> Unit,
) {
    if (!enabled) {
        board(modifier)
        return
    }
    val dock = remember { SelfRowDock() }
    Box(modifier.onGloballyPositioned { dock.boardPlaced(it) }) {
        CompositionLocalProvider(LocalSelfRowDock provides dock) { board(Modifier) }
        if (self != null) Dock(dock, self, order, withPuzzleBoxRank, medals, animated, backdrop)
    }
}

@Composable
private fun Dock(
    dock: SelfRowDock,
    self: DailyBoardLine.Real,
    order: RankOrder,
    withPuzzleBoxRank: Boolean,
    medals: Boolean,
    animated: Boolean,
    backdrop: Color,
) {
    val instant = rememberReducedMotion() || LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    val from = if (dock.side == DockEdge.Top) -1 else 1
    AnimatedVisibility(
        visible = dock.edge != null,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                val y = if (dock.side == DockEdge.Top) dock.visibleTop else dock.visibleBottom - placeable.height
                placeable.place(0, y.roundToInt())
            }
        },
        enter = if (instant) {
            EnterTransition.None
        } else {
            fadeIn(BoardMotion.fadeIn) + slideInVertically(tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)) { from * it / 2 }
        },
        exit = if (instant) ExitTransition.None else fadeOut(BoardMotion.fadeOut),
    ) {
        DockBar(self, order, withPuzzleBoxRank, medals, animated, backdrop) { scope.launch { dock.requester.bringIntoView() } }
    }
}

/** The docked row: the viewer's own row, lifted off the board it covers, one tap from where it is. */
@Composable
internal fun DockBar(
    self: DailyBoardLine.Real,
    order: RankOrder,
    withPuzzleBoxRank: Boolean,
    medals: Boolean,
    animated: Boolean,
    backdrop: Color,
    onJump: () -> Unit,
) {
    val place = if (self.row.beyondPage) BeyondPageLabel else "#${self.place}"
    Box(
        Modifier
            .testTag("leaderboard.dock")
            .shadow(Spacing.xs, MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Jump to your row", role = Role.Button, onClick = onJump)
            .clearAndSetSemantics { contentDescription = "Your row, $place. Jump to it" },
    ) {
        // The copy is not the row the dock follows, and it is opaque: it covers rows that are not drawn on a backdrop.
        CompositionLocalProvider(
            LocalSelfRowDock provides null,
            LocalBoardBackdrop provides backdrop,
            LocalPlaceMotion provides if (animated) StillPlaces else null,
        ) { BoardLine(self, order, withPuzzleBoxRank, medals) }
    }
}

private val PreviewSelf = DailyBoardLine.Real(DailyBoardRowModel(41, "You", 188_000L, 3, isSelf = true, playerId = "you"), place = 41)

// The docked row as it lies over the board at the top and at the bottom; the scrolling it follows needs a device.
@PuzzleBoxPreviews
@Composable
private fun DockedSelfRowPreview() = PuzzleBoxTheme {
    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        DockBar(PreviewSelf, RankOrder.TimeThenHints, false, false, false, MaterialTheme.colorScheme.background) {}
        DockBar(PreviewSelf, RankOrder.TimeThenHints, false, true, true, MaterialTheme.colorScheme.background) {}
        DockBar(
            PreviewSelf.copy(row = PreviewSelf.row.copy(beyondPage = true, rank = 120)),
            RankOrder.TimeThenHints, false, false, false, MaterialTheme.colorScheme.background,
        ) {}
    }
}

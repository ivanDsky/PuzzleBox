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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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

/**
 * [board] with the viewer's [self] row docked to the top or bottom edge of the viewport while it is scrolled out of
 * view, when [enabled]: a copy of the row laid over the board at that edge, which jumps to the row when tapped. The
 * board is drawn exactly as it is without a dock, and the row in it is the one at its own place, so the dock never
 * changes what the board animates. [board] gets the modifier the board is to carry. Between two boards the dock is
 * the row of [scrub] instead, and stays at the edge it was docked to.
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
    scrub: DockScrub? = null,
    board: @Composable (Modifier) -> Unit,
) {
    if (!enabled) {
        board(modifier)
        return
    }
    val dock = remember { SelfRowDock() }
    Box(modifier.onGloballyPositioned { dock.boardPlaced(it) }) {
        CompositionLocalProvider(LocalSelfRowDock provides dock) { board(Modifier) }
        val row = scrub?.self ?: self
        if (row != null) Dock(dock, row, scrub?.order ?: order, scrub?.withPuzzleBoxRank ?: withPuzzleBoxRank, medals, animated, backdrop, scrub)
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
    scrub: DockScrub?,
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
        DockBar(self, order, withPuzzleBoxRank, medals, animated, backdrop, scrub) { scope.launch { dock.requester.bringIntoView() } }
    }
}


/**
 * The docked row: the viewer's own row, lifted off the board it covers, one tap from where it is. On an [animated]
 * board a new place rolls and a new time counts in it, as in the row it copies. With a [scrub] the scrub is the clock:
 * its place rolls and its other cells frost over from what they were, or the whole row frosts out, as far as it says.
 */
@Composable
internal fun DockBar(
    self: DailyBoardLine.Real,
    order: RankOrder,
    withPuzzleBoxRank: Boolean,
    medals: Boolean,
    animated: Boolean,
    backdrop: Color,
    scrub: DockScrub? = null,
    onJump: () -> Unit,
) {
    val place = if (self.row.beyondPage) BeyondPageLabel else "#${self.place}"
    val leaving = scrub?.takeIf { it.leaves }?.progress
    val still = scrub != null || rememberReducedMotion() || LocalInspectionMode.current
    Box(
        Modifier
            .testTag("leaderboard.dock")
            .then(if (leaving != null) Modifier.frostedOut(leaving) else Modifier)
            .shadow(Spacing.xs, MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Jump to your row", role = Role.Button, onClick = onJump)
            .clearAndSetSemantics { contentDescription = "Your row, $place. Jump to it" },
    ) {
        // The copy is not the row the dock follows, and it is opaque: it covers rows that are not drawn on a backdrop.
        CompositionLocalProvider(
            LocalSelfRowDock provides null,
            LocalBoardBackdrop provides backdrop,
            LocalPlaceMotion provides if (!animated) null else if (still) StillPlaces else DockPlaces,
            LocalRowScrub provides scrub?.was,
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

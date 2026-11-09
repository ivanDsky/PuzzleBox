package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.Spacing

private enum class PaneSlot { Toolbar, Board }

/**
 * The two-pane board pane (A15): [toolbar] under the standard top inset, then [board] in
 * the height left, given its cap — min(pane width, pane height − toolbar), less the
 * [PlayBoardPane] insets and side gutters. The toolbar is as wide as that cap and centred,
 * so it spans the board's columns; the cap needs the toolbar's height first, hence the
 * subcomposition.
 */
@Composable
internal fun ToolbarBoardPane(
    toolbar: (@Composable () -> Unit)?,
    board: @Composable BoxScope.(paneCap: Dp?) -> Unit,
    modifier: Modifier,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val inset = Spacing.lg.roundToPx()
        val bar = if (toolbar != null) {
            subcompose(PaneSlot.Toolbar) { Box(Modifier.testTag("play.toolbar")) { toolbar() } }
        } else {
            emptyList()
        }
        val barHeight = bar.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
        val boardTop = if (bar.isEmpty()) 0 else inset + barHeight
        val boardHeight = (height - boardTop).coerceAtLeast(0)
        val cap = minOf(width - 2 * inset, boardHeight - 2 * inset).coerceAtLeast(0)
        val capDp = cap.toDp()
        val boardPlaceables = subcompose(PaneSlot.Board) {
            Box(Modifier.fillMaxSize()) { board(capDp) }
        }.map { it.measure(Constraints.fixed(width, boardHeight)) }
        val barPlaceables = bar.map { it.measure(Constraints(minWidth = cap, maxWidth = cap, maxHeight = barHeight)) }
        layout(width, height) {
            barPlaceables.forEach { it.placeRelative(Alignment.CenterHorizontally.align(it.width, width, layoutDirection), inset) }
            boardPlaceables.forEach { it.placeRelative(0, boardTop) }
        }
    }
}

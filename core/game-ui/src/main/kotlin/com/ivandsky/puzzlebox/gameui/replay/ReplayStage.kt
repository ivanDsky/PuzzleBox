package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

private val BoardGap = Spacing.xxl
private val ControlsGap = Spacing.lg

/** The narrowest the controls stay usable; a small board does not squeeze them below it. */
private val MinControlsWidth = 340.dp

/**
 * The replay page's body: [boardCount] boards side by side at the largest square size that still leaves room for
 * the [controls] below, the pair centred. The controls are as wide as the boards, one shared block for all of them.
 * [controlsHeight] is the height the controls take. [boardHeaderHeight] is a header the caller draws over each
 * board inside [board]; the board's square [size] leaves room for it.
 */
@Composable
internal fun ReplayStage(
    boardCount: Int,
    modifier: Modifier = Modifier,
    controlsHeight: Dp = ReplayTransportHeight,
    boardHeaderHeight: Dp = 0.dp,
    controls: @Composable () -> Unit,
    board: @Composable (index: Int, size: Dp) -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val gaps = BoardGap * (boardCount - 1)
        val size = minOf((maxWidth - gaps) / boardCount, maxHeight - boardHeaderHeight - controlsHeight - ControlsGap).coerceAtLeast(0.dp)
        val boardsWidth = size * boardCount + gaps
        Column(Modifier.width(maxOf(boardsWidth, minOf(maxWidth, MinControlsWidth))), verticalArrangement = Arrangement.spacedBy(ControlsGap)) {
            Row(Modifier.width(boardsWidth).align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(BoardGap)) {
                repeat(boardCount) { board(it, size) }
            }
            controls()
        }
    }
}

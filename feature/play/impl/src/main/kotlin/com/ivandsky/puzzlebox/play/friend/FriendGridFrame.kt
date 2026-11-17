package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.TwoColumnFrame

private enum class Slot { Board, Below }

/**
 * The two columns of the friend page on a pane [paneHeight] high, shared by the page and its loading skeleton so the
 * one replaces the other in place. It is the tablet panes' [TwoColumnFrame] with the [board] ([FriendBoardColumn] gives
 * it the largest side that lets it, the chips and the time tiles in [below] fit the pane) over [below] on the left, and
 * [right] in the [com.ivandsky.puzzlebox.designsystem.TwoColumnRightWidth] column. [tag] marks the row, `[tag].left`
 * and `[tag].right` the columns.
 */
@Composable
internal fun FriendGridFrame(
    paneHeight: Dp,
    tag: String,
    board: @Composable (maxSide: Dp) -> Unit,
    below: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    TwoColumnFrame(
        paneHeight = paneHeight,
        tag = tag,
        left = { column, height -> FriendBoardColumn(height, board, below, column) },
        right = right,
        modifier = modifier,
    )
}

/**
 * The board over [below], a [Spacing.lg] apart. [below] is measured first, so the board is given the side that leaves
 * it room in [height]: never less than [HiddenBoardTokens.MaxSide], today's size, in a pane too short for more.
 */
@Composable
private fun FriendBoardColumn(
    height: Dp,
    board: @Composable (maxSide: Dp) -> Unit,
    below: @Composable ColumnScope.() -> Unit,
    modifier: Modifier,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val gap = Spacing.lg.roundToPx()
        val under = subcompose(Slot.Below) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), content = below)
        }.map { it.measure(Constraints(maxWidth = width)) }
        val underHeight = under.sumOf { it.height }
        val room = (height.roundToPx() - underHeight - gap).toDp()
        val over = subcompose(Slot.Board) { board(maxOf(HiddenBoardTokens.MaxSide, room)) }
            .map { it.measure(Constraints(maxWidth = width)) }
        val overHeight = over.sumOf { it.height }
        layout(width, overHeight + gap + underHeight) {
            over.forEach { it.place(0, 0) }
            under.forEach { it.place(0, overHeight + gap) }
        }
    }
}

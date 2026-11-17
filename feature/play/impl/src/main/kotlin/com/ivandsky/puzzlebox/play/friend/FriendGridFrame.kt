package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The right column's width (prototype 05, "Tablet"). */
internal val FriendGridRightWidth = 372.dp

/** The narrowest board column that still reads as a board: below it the page keeps the single column. */
private val FriendGridMinBoardWidth = 320.dp

private val FriendGridGap = Spacing.xl

/** The page's margin above and below the columns, kept even when they fill the pane. */
private val FriendGridMargin = Spacing.lg

/** The pane width that fits both columns at the page's margins: board column, gap, right column and both margins. */
internal val FriendGridMinWidth = FriendGridMinBoardWidth + FriendGridGap + FriendGridRightWidth + Spacing.lg * 2

private enum class Slot { Board, Below }

/**
 * The two columns of the friend page on a pane [paneHeight] high, shared by the page and its loading skeleton so the
 * one replaces the other in place. The left column is the [board] ([FriendBoardColumn] gives it the largest side that
 * lets it, the chips and the time tiles in [below] fit the pane) over [below]; the right is [right], [FriendGridRightWidth]
 * wide. The columns are centred on each other and, when shorter than the pane, on the pane; taller, the page scrolls
 * from the top. [tag] marks the row, `[tag].left` and `[tag].right` the columns.
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
    Box(modifier.fillMaxWidth().heightIn(min = paneHeight), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = FriendGridMargin).testTag(tag),
            horizontalArrangement = Arrangement.spacedBy(FriendGridGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FriendBoardColumn(paneHeight - FriendGridMargin * 2, board, below, Modifier.weight(1f).testTag("$tag.left"))
            Column(
                Modifier.width(FriendGridRightWidth).testTag("$tag.right"),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                content = right,
            )
        }
    }
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

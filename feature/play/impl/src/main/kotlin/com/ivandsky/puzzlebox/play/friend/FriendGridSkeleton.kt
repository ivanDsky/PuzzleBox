package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButtonMinHeight
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.skeleton
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoardLoading

/**
 * The friend page's two columns while the code opens, for a pane wide enough for [FriendPuzzleGrid]: the loading board
 * in the left column at the side the loaded board will have, over quiet bars for the chips and the time tile; in the
 * right column bars for the head, the title and byline, the action and the ranks. It is [FriendGridFrame], so the
 * loaded page replaces it in place; each bar takes the text style of what it holds, so it is as high.
 */
@Composable
internal fun FriendGridSkeleton(paneHeight: Dp, modifier: Modifier = Modifier) {
    FriendGridFrame(
        paneHeight = paneHeight,
        tag = "friend.card.grid.skeleton",
        modifier = modifier,
        board = { side ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                HiddenBoardLoading(Modifier.testTag("friend.card.loading.board"), maxSide = side)
            }
        },
        below = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Bar(Modifier.width(96.dp), MaterialTheme.typography.labelLarge, PillShape, Spacing.xs)
                Bar(Modifier.width(64.dp), MaterialTheme.typography.labelLarge, PillShape, Spacing.xs)
            }
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Bar(Modifier.width(48.dp), MaterialTheme.typography.labelSmall)
                Bar(Modifier.width(96.dp), MaterialTheme.typography.titleLarge)
                Bar(Modifier.width(64.dp), MaterialTheme.typography.bodyMedium)
            }
        },
        right = {
            Block(Modifier.fillMaxWidth().height(HEAD_HEIGHT).testTag("friend.card.grid.skeleton.head"), MaterialTheme.shapes.medium)
            Column(Modifier.testTag("friend.card.grid.skeleton.title"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Bar(Modifier.fillMaxWidth(TITLE_FRACTION), MaterialTheme.typography.headlineMedium)
                Bar(Modifier.width(120.dp), MaterialTheme.typography.bodyLarge)
            }
            NoteSkeleton(Modifier.testTag("friend.card.grid.skeleton.note"))
            Block(Modifier.fillMaxWidth().height(PrimaryButtonMinHeight).testTag("friend.card.grid.skeleton.action"), MaterialTheme.shapes.large)
            Bar(Modifier.padding(top = Spacing.sm).width(CAPTION_WIDTH).align(Alignment.CenterHorizontally), MaterialTheme.typography.bodyMedium)
            RanksSkeleton(Modifier.testTag("friend.card.grid.skeleton.ranks"))
        },
    )
}

/** A bar one line of [style] high, with [vertical] padding as a chip has; silent to a screen reader. */
@Composable
private fun Bar(modifier: Modifier, style: TextStyle, shape: Shape = MaterialTheme.shapes.small, vertical: Dp = 0.dp) {
    Box(modifier.skeleton(MaterialTheme.colorScheme.surfaceContainerHigh, shape).clearAndSetSemantics {}.padding(vertical = vertical)) {
        Text(" ", style = style)
    }
}

@Composable
private fun Block(modifier: Modifier, shape: Shape) {
    Box(modifier.skeleton(MaterialTheme.colorScheme.surfaceContainerHigh, shape).clearAndSetSemantics {})
}

/** The note's card: one bodyLarge line between the card's padding, as the loaded note is; a puzzle may have none, but the skeleton cannot know. */
@Composable
private fun NoteSkeleton(modifier: Modifier) {
    Box(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small).padding(Spacing.md)) {
        Bar(Modifier.fillMaxWidth(NOTE_FRACTION), MaterialTheme.typography.bodyLarge)
    }
}

/** The ranks card: its label over [RANK_ROWS] two-line rows with a [BoardLine]'s padding; no author line, so the rows stand for the author and the first ranks. */
@Composable
private fun RanksSkeleton(modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Bar(Modifier.width(48.dp), MaterialTheme.typography.labelSmall)
        repeat(RANK_ROWS) {
            Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                Bar(Modifier.width(120.dp), MaterialTheme.typography.bodyLarge)
                Bar(Modifier.width(72.dp), MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private val HEAD_HEIGHT = 48.dp
private const val TITLE_FRACTION = 0.7f
private const val NOTE_FRACTION = 0.8f
private const val RANK_ROWS = 3
private val CAPTION_WIDTH = 160.dp

package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
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
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButtonMinHeight
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.skeleton
import com.ivandsky.puzzlebox.designsystem.TwoColumnMinWidth
import com.ivandsky.puzzlebox.gameui.PlayLayout

private val AvatarBlock = 40.dp
private val CardBlockHeight = 104.dp
private val HeroBlock = 64.dp
private const val SKELETON_CARDS = 3
private const val TITLE_FRACTION = 0.7f

/**
 * The slot while the duel opens: on a tablet pane wide enough for the two columns ([DuelLegs]) the page's skeleton,
 * [DuelLegsSkeleton]; otherwise the spinner, centred.
 */
@Composable
internal fun DuelLoading(layout: PlayLayout, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (layout == PlayLayout.TwoPane && maxWidth >= TwoColumnMinWidth) {
            DuelLegsSkeleton()
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

/**
 * The duel's two panes while it loads: quiet bars for the header, the list's cards and the board and detail, in the same
 * [DuelWideFrame] as [DuelLegs], so the loaded page replaces it in place. Each bar takes the text style of what it holds,
 * so it is as high.
 */
@Composable
internal fun DuelLegsSkeleton(modifier: Modifier = Modifier) {
    DuelWideFrame(
        tag = "$DUEL_GRID_TAG.skeleton",
        modifier = modifier,
        left = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Block(Modifier.size(AvatarBlock), PillShape)
                    Column(Modifier.weight(1f)) {
                        Bar(Modifier.fillMaxWidth(TITLE_FRACTION), MaterialTheme.typography.headlineSmall)
                        Bar(Modifier.width(160.dp), MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Bar(Modifier.width(120.dp), MaterialTheme.typography.labelMedium, PillShape, Spacing.xs)
                    Bar(Modifier.width(180.dp), MaterialTheme.typography.labelMedium, PillShape, Spacing.xs)
                }
                Bar(Modifier.width(72.dp), MaterialTheme.typography.titleMedium)
                repeat(SKELETON_CARDS) { Block(Modifier.fillMaxWidth().height(CardBlockHeight), AppShapes.large) }
            }
        },
        right = { height ->
            BoardOverDetails(
                height = height,
                board = { side ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Block(
                            Modifier.testTag("$DUEL_GRID_TAG.skeleton.board").widthIn(max = side).fillMaxWidth().aspectRatio(1f),
                            MaterialTheme.shapes.medium,
                        )
                    }
                },
                details = {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Block(Modifier.size(HeroBlock), AppShapes.large)
                        Bar(Modifier.fillMaxWidth(TITLE_FRACTION), MaterialTheme.typography.headlineSmall)
                        Bar(Modifier.width(160.dp), MaterialTheme.typography.bodyMedium)
                        Bar(Modifier.width(120.dp), MaterialTheme.typography.labelMedium, PillShape, Spacing.xs)
                        Bar(Modifier.width(200.dp), MaterialTheme.typography.bodyMedium)
                        Block(Modifier.fillMaxWidth().height(PrimaryButtonMinHeight), AppShapes.large)
                    }
                },
            )
        },
    )
}

/** A bar one line of [style] high, with [vertical] padding as a pill has; silent to a screen reader. */
@Composable
private fun Bar(modifier: Modifier, style: TextStyle, shape: Shape = MaterialTheme.shapes.small, vertical: Dp = 0.dp) {
    Box(
        modifier
            .skeleton(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .clearAndSetSemantics {}
            .padding(vertical = vertical),
    ) {
        Text(" ", style = style)
    }
}

@Composable
private fun Block(modifier: Modifier, shape: Shape) {
    Box(modifier.skeleton(MaterialTheme.colorScheme.surfaceContainerHigh, shape).clearAndSetSemantics {})
}

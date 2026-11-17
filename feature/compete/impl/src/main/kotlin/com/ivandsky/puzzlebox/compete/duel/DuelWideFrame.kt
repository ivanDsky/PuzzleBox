package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** Marks the wide page: `duel.legs.grid`, with `.left` and `.right` for its panes; its loading skeleton adds `.skeleton`. */
internal const val DUEL_GRID_TAG = "duel.legs.grid"

/**
 * The tablet duel page, edge to edge: two equal panes either side of a full-height divider. Each pane centres its content
 * down the pane as one block when it is shorter, and starts it at the top and scrolls when it is taller. [right] is told
 * the height the pane leaves it inside its padding, which the board preview sizes to.
 */
@Composable
internal fun DuelWideFrame(
    tag: String,
    left: @Composable () -> Unit,
    right: @Composable (height: Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize().testTag(tag)) {
        Pane(Modifier.weight(1f).testTag("$tag.left")) { left() }
        VerticalDivider(Modifier.testTag("duel.legs.divider"), color = MaterialTheme.colorScheme.outlineVariant)
        Pane(Modifier.weight(1f).testTag("$tag.right")) { height -> right(height) }
    }
}

@Composable
private fun Pane(modifier: Modifier, content: @Composable (height: Dp) -> Unit) {
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val height = maxHeight - Spacing.lg * 2
        Column(
            Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
        ) { content(height) }
    }
}

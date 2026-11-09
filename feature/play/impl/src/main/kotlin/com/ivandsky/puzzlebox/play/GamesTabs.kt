package com.ivandsky.puzzlebox.play

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular

private val PillMinHeight = 40.dp
private val BadgeSize = 18.dp
private const val BADGE_CAP = 9

/** The pills of the Games screen's tabs, side by side with the screen's side padding. */
@Composable
internal fun GamesTabs(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** One tab: [label] then an optional [trailing] slot (the count); [tag] is its test tag. */
@Composable
internal fun GamesTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val fade = tween<Color>(if (reduced) 0 else MotionTokens.SelectDuration, easing = MotionTokens.EasingStandard)
    val container by animateColorAsState(if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh, fade, label = "tab-container")
    val content by animateColorAsState(if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant, fade, label = "tab-content")
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = PillShape,
        color = container,
        contentColor = content,
        modifier = modifier
            .pressScale(interaction, reduced)
            .clip(PillShape)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .testTag(tag),
    ) {
        Row(
            Modifier.heightIn(min = PillMinHeight).padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            trailing?.invoke()
        }
    }
}

/** The red need-you badge: nothing for 0, the number up to nine, "9+" above. */
@Composable
internal fun TabCountBadge(count: Int, modifier: Modifier = Modifier) {
    val text = tabCountText(count) ?: return
    Box(
        modifier = modifier
            .widthIn(min = BadgeSize)
            .heightIn(min = BadgeSize)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold).tabular(),
            color = MaterialTheme.colorScheme.onError,
            maxLines = 1,
        )
    }
}

/** The badge text for [count]: null for none, capped at "9+". */
internal fun tabCountText(count: Int): String? = when {
    count <= 0 -> null
    count > BADGE_CAP -> "$BADGE_CAP+"
    else -> count.toString()
}

@PuzzleBoxPreviews
@Composable
internal fun GamesTabsPreview() {
    PuzzleBoxTheme {
        Surface {
            GamesTabs {
                GamesTabPill("Duels", selected = true, onClick = {}, tag = "preview.duels", trailing = { TabCountBadge(12) })
                GamesTabPill("Friends", selected = false, onClick = {}, tag = "preview.friends", trailing = {
                    Text("3", style = MaterialTheme.typography.labelLarge.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                })
                GamesTabPill("Library", selected = false, onClick = {}, tag = "preview.library")
            }
        }
    }
}

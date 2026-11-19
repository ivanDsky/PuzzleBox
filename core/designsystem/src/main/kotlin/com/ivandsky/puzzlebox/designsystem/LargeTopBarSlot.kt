package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable

/**
 * The screen-opening bar around a [title] and [subtitle] the screen draws itself: a title that is more than a text (a
 * menu) or a sub line that acts. The one-text `LargeTopBar` in `TopBars.kt` is this bar with plain texts. Main tabs
 * fill the slots with [LargeTopBarTitle] and [LargeTopBarSubtitle], so every tab keeps the one title size and the one
 * bar height however long its title or how many its actions; [LocalShellActions] follow the screen's [actions].
 */
@Composable
fun LargeTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .testTag("topbar.large")
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Box(Modifier.testTag("topbar.title")) { title() }
            subtitle?.invoke()
        }
        actions()
        LocalShellActions.current.invoke(this)
    }
}

// Preview: a main tab's bar on a 360dp phone. A short title keeps the display size; a long one with three actions
// shrinks and the sub line ends in an ellipsis, at the same bar height.
@ShowkaseComposable(name = "LargeTopBarSlot", group = "TopBars")
@PuzzleBoxPreviews
@Composable
internal fun LargeTopBarSlotPreview() {
    PuzzleBoxTheme {
        Column(Modifier.width(360.dp)) {
            LargeTopBar(
                title = { LargeTopBarTitle("Games") },
                subtitle = { LargeTopBarSubtitle("Every puzzle, back to #1") },
                actions = { SecondaryButton(text = "Enter code", onClick = {}) },
            )
            LargeTopBar(
                title = { LargeTopBarTitle("Community") },
                subtitle = { LargeTopBarSubtitle("Puzzles from you and your friends", onClick = {}, onClickLabel = "Tune") },
                actions = {
                    SecondaryButton(text = "Enter code", onClick = {})
                    IconButton(onClick = {}) {
                        Icon(PuzzleIcons.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {}) {
                        Icon(PuzzleIcons.Profile, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        }
    }
}

package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * Screen-opening bar: serif display title (the editorial voice from A12) with an
 * optional subtitle and trailing actions. For top-level screens. It is the slot `LargeTopBar` in
 * `LargeTopBarSlot.kt` with plain texts, so a long [title] wraps here instead of shrinking.
 */
@Composable
fun LargeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeTopBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        modifier = modifier,
        subtitle = subtitle?.let { line ->
            {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = actions,
    )
}

/**
 * Detail-screen bar: 48dp navigation and action targets around a single title.
 * The screen supplies the icon buttons so call sites keep their own onClick.
 */
@Composable
fun CompactTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CompactTopBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

// Previews — Studio tooling only, zero public API surface (internal, not private, so
// Showkase's generated index can access them; ticket 00 visibility findings). Exactly
// light + dark, always inside PuzzleBoxTheme (spec A1/A5). A8: neither top bar animates
// — no infinite transition, no trigger — so static panes show the resting state.

@ShowkaseComposable(name = "LargeTopBar", group = "TopBars")
@PuzzleBoxPreviews
@Composable
internal fun LargeTopBarPreview() {
    PuzzleBoxTheme {
        LargeTopBar(
            title = "Today",
            subtitle = "Friday, October 31",
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = PuzzleIcons.Appearance,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@ShowkaseComposable(name = "CompactTopBar", group = "TopBars")
@PuzzleBoxPreviews
@Composable
internal fun CompactTopBarPreview() {
    PuzzleBoxTheme {
        CompactTopBar(
            title = "Daily Zip",
            navigationIcon = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = PuzzleIcons.Back,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@ShowkaseComposable(name = "TopBarsShellActions", group = "TopBars")
@PuzzleBoxPreviews
@Composable
internal fun TopBarsShellActionsPreview() {
    PuzzleBoxTheme {
        CompositionLocalProvider(
            LocalShellActions provides {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = PuzzleIcons.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LargeTopBar(
                    title = "Today",
                    subtitle = "Friday, October 31",
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = PuzzleIcons.Appearance,
                                contentDescription = "Appearance",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                CompactTopBar(
                    title = "Daily Zip",
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = PuzzleIcons.Appearance,
                                contentDescription = "Appearance",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
    }
}

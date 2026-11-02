package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Screen-opening bar: serif display title (the editorial voice from A12) with an
 * optional subtitle and trailing actions. For top-level screens.
 */
@Composable
fun LargeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (navigationIcon != null) {
            navigationIcon()
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Catalog demo: the screen-opening and detail bars in their catalog cards. */
@Composable
internal fun TopBarsCatalogSection() {
    CatalogSection(title = "Top bars") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CatalogCard {
                LargeTopBar(
                    title = "Today",
                    subtitle = "Friday, October 31",
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
            CatalogCard {
                CompactTopBar(
                    title = "Daily Zip",
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
    }
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
                        imageVector = Icons.Filled.Settings,
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

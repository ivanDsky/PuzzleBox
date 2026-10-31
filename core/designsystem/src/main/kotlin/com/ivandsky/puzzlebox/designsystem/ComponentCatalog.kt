package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Standalone catalog page for the shared components (tokens live in [TokenCatalogScreen]). */
@Composable
fun ComponentCatalogScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "Components",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "The shared pieces every game screen reuses — one token set, dark and light.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ComponentCatalogSections()
    }
}

/** The component sections, appended to the full catalog screen after the token sections. */
@Composable
fun ComponentCatalogSections() {
    CatalogSection(title = "Buttons") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PrimaryButton(text = "Start puzzle", onClick = {})
            SecondaryButton(text = "View rules", onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PrimaryButton(text = "Disabled", onClick = {}, enabled = false)
                SecondaryButton(text = "Disabled", onClick = {}, enabled = false)
            }
        }
    }

    CatalogSection(title = "Chips") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ScoreChip(value = "1:24", label = "Score")
            HintChip(count = 3)
        }
    }

    CatalogSection(title = "Puzzle board") {
        var path by remember { mutableStateOf(listOf<Pair<Int, Int>>()) }
        val scheme = MaterialTheme.colorScheme
        // Play-sized board: capped to 360dp and centered instead of filling wide panes.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PuzzleBoard(
                cellsPerSide = 4,
                modifier = Modifier.widthIn(max = 360.dp),
                onCellTap = { row, col -> path = listOf(row to col) },
                onCellDragStart = { row, col -> path = listOf(row to col) },
                onCellDrag = { row, col -> path = path + (row to col) },
                cellContent = { row, col ->
                    when {
                        row == 0 && col == 0 -> EndpointCell("A")
                        row == 3 && col == 3 -> EndpointCell("B")
                        (row to col) in path -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(scheme.primary),
                                )
                            }
                        }
                    }
                },
            )
        }
        Text(
            text = "Tap or drag across the cells — the trace above is the surface the Zip path gesture will build on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    CatalogSection(title = "Dialog") {
        DialogCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Reset puzzle?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Your current path and timer clear. This can't be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Spacer(Modifier.weight(1f))
                SecondaryButton(text = "Cancel", onClick = {})
                PrimaryButton(text = "Reset", onClick = {})
            }
        }
    }

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

    CatalogSection(title = "Empty & error states") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CatalogCard {
                EmptyState(
                    title = "No games yet",
                    message = "Finished puzzles and scores will appear here.",
                    actionLabel = "Browse dailies",
                    onAction = {},
                )
            }
            CatalogCard {
                ErrorState(
                    title = "Couldn't load today's puzzle",
                    message = "Check your connection and try again.",
                    onRetry = {},
                )
            }
        }
    }
}

@Composable
private fun EndpointCell(label: String) {
    // fillMaxSize + center: the marker must sit mid-cell, not top-start.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun CatalogCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        content()
    }
}

// Previews — Studio tooling only, zero public API surface (internal, not private, so
// Showkase's generated index can access them; ticket 00 visibility findings). Exactly
// light + dark, always inside PuzzleBoxTheme (spec A1/A5). The catalogs are scrollable
// showcases, so each preview constrains a representative phone viewport (360×640dp)
// instead of rendering the full scroll height. A8: nothing in these sections animates
// on its own — the board cells and state cards are static, every motion is user-driven
// — so the static panes show the resting state.

@ShowkaseComposable(name = "ComponentCatalogScreen", group = "Catalogs")
@PuzzleBoxPreviews
@Composable
internal fun ComponentCatalogScreenPreview() {
    PuzzleBoxTheme {
        Box(Modifier.size(360.dp, 640.dp)) {
            ComponentCatalogScreen()
        }
    }
}

@ShowkaseComposable(name = "ComponentCatalogSections", group = "Catalogs")
@PuzzleBoxPreviews
@Composable
internal fun ComponentCatalogSectionsPreview() {
    PuzzleBoxTheme {
        Box(Modifier.size(360.dp, 640.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                ComponentCatalogSections()
            }
        }
    }
}

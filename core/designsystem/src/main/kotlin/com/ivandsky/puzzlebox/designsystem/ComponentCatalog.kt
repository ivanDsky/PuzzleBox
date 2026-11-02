package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Standalone catalog page for the shared components (tokens live in [TokenCatalogScreen]).
 * Test/preview surface, hence internal: no route consumes it — the app's Catalog route
 * shows [TokenCatalogScreen] and the Showkase browser indexes the previews below.
 */
@Composable
internal fun ComponentCatalogScreen(modifier: Modifier = Modifier) {
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

/**
 * The component families, one co-located section per file (registration path): adding
 * a family means its own file — e.g. `FooCatalogSection()` in Foo.kt — plus exactly one
 * line here. No hand-grown demo block accumulates in this file.
 */
@Composable
internal fun ComponentCatalogSections() {
    ButtonsCatalogSection()
    ChipsCatalogSection()
    PuzzleBoardCatalogSection()
    DialogsCatalogSection()
    TopBarsCatalogSection()
    StatesCatalogSection()
}

/** Card wrapper shared by the dialog, top-bar, and state catalog sections. */
@Composable
internal fun CatalogCard(content: @Composable () -> Unit) {
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

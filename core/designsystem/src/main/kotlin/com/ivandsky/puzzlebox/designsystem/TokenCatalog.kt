package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

// Renders the complete token set (A12): color, type, shape, spacing, motion. Component
// demos are NOT appended here — they live in [ComponentCatalogScreen] (catalog-split
// finding: this screen must render what its name says). It draws whatever theme wraps
// it, so dark and light both come from the same tree.
@Composable
fun TokenCatalogScreen(
    modifier: Modifier = Modifier,
) {
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
                text = "Design system",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Every color, type style, corner, gap, and motion the app is built from — one token set, dark and light.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CatalogSection(title = "Color") {
            colorPairSpecs().forEach { pair ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp, 56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(pair.container),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.titleMedium,
                            color = pair.on,
                        )
                    }
                    Column {
                        Text(
                            text = pair.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "on: ${pair.onName} · ${pair.on.toHex6()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = pair.container.toHex6(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            ColorTierRow()
        }

        CatalogSection(title = "Typography") {
            typographySamples().forEach { (name, style) ->
                Column {
                    Text(
                        text = "$name · ${style.fontSize.value.toInt()}sp / ${style.lineHeight.value.toInt()}sp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Puzzle 12",
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        CatalogSection(title = "Shape") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                listOf(
                    "extraSmall" to MaterialTheme.shapes.extraSmall,
                    "small" to MaterialTheme.shapes.small,
                    "medium" to MaterialTheme.shapes.medium,
                    "large" to MaterialTheme.shapes.large,
                    "extraLarge" to MaterialTheme.shapes.extraLarge,
                ).forEach { (name, shape) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        CatalogSection(title = "Spacing") {
            listOf(
                "xs" to Spacing.xs,
                "sm" to Spacing.sm,
                "md" to Spacing.md,
                "lg" to Spacing.lg,
                "xl" to Spacing.xl,
                "xxl" to Spacing.xxl,
            ).forEach { (name, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        modifier = Modifier
                            .width(value)
                            .height(Spacing.lg)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = "$name · ${value.value.toInt()}dp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        MotionCatalogSection()
    }
}

@Composable
internal fun CatalogSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

private data class ColorPairSpec(val name: String, val onName: String, val container: Color, val on: Color)

@Composable
private fun colorPairSpecs(): List<ColorPairSpec> {
    val s = MaterialTheme.colorScheme
    return listOf(
        ColorPairSpec("primary", "onPrimary", s.primary, s.onPrimary),
        ColorPairSpec("primaryContainer", "onPrimaryContainer", s.primaryContainer, s.onPrimaryContainer),
        ColorPairSpec("secondary", "onSecondary", s.secondary, s.onSecondary),
        ColorPairSpec("secondaryContainer", "onSecondaryContainer", s.secondaryContainer, s.onSecondaryContainer),
        ColorPairSpec("tertiary", "onTertiary", s.tertiary, s.onTertiary),
        ColorPairSpec("tertiaryContainer", "onTertiaryContainer", s.tertiaryContainer, s.onTertiaryContainer),
        ColorPairSpec("error", "onError", s.error, s.onError),
        ColorPairSpec("errorContainer", "onErrorContainer", s.errorContainer, s.onErrorContainer),
        ColorPairSpec("surface", "onSurface", s.surface, s.onSurface),
        ColorPairSpec("surfaceVariant", "onSurfaceVariant", s.surfaceVariant, s.onSurfaceVariant),
        ColorPairSpec("inverseSurface", "inverseOnSurface", s.inverseSurface, s.inverseOnSurface),
    )
}

@Composable
private fun ColorTierRow() {
    val s = MaterialTheme.colorScheme
    val tiers = listOf(
        "surfaceDim" to s.surfaceDim,
        "surfaceBright" to s.surfaceBright,
        "surfaceContainerLowest" to s.surfaceContainerLowest,
        "surfaceContainerLow" to s.surfaceContainerLow,
        "surfaceContainer" to s.surfaceContainer,
        "surfaceContainerHigh" to s.surfaceContainerHigh,
        "surfaceContainerHighest" to s.surfaceContainerHighest,
        "outline" to s.outline,
        "outlineVariant" to s.outlineVariant,
        "inversePrimary" to s.inversePrimary,
        "scrim" to s.scrim,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        tiers.forEach { (name, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp, 24.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
                )
                Text(
                    text = "$name · ${color.toHex6()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun typographySamples(): List<Pair<String, TextStyle>> {
    val t = MaterialTheme.typography
    return listOf(
        "displayLarge" to t.displayLarge,
        "displayMedium" to t.displayMedium,
        "displaySmall" to t.displaySmall,
        "headlineLarge" to t.headlineLarge,
        "headlineMedium" to t.headlineMedium,
        "headlineSmall" to t.headlineSmall,
        "titleLarge" to t.titleLarge,
        "titleMedium" to t.titleMedium,
        "titleSmall" to t.titleSmall,
        "bodyLarge" to t.bodyLarge,
        "bodyMedium" to t.bodyMedium,
        "bodySmall" to t.bodySmall,
        "labelLarge" to t.labelLarge,
        "labelMedium" to t.labelMedium,
        "labelSmall" to t.labelSmall,
    )
}

// Previews — Studio tooling only, zero public API surface (internal, not private, so
// Showkase's generated index can access them; ticket 00 visibility findings). Exactly
// light + dark, always inside PuzzleBoxTheme (spec A1/A5). The catalog is a scrollable
// showcase, so the preview constrains a representative phone viewport (360×640dp)
// instead of rendering the full scroll height. A4: TokenCatalogScreen is public, so the
// catalog gets previews. A8: the only animation in the tree is the tap-driven
// SpringDemo track (rendered via [MotionCatalogSection]) — touch-triggered like the
// buttons (ticket 01), so there is no programmatic trigger a preview could start; the
// static panes show the resting state and interactive mode runs the spring on tap.

@ShowkaseComposable(name = "TokenCatalogScreen", group = "Catalogs")
@PuzzleBoxPreviews
@Composable
internal fun TokenCatalogScreenPreview() {
    PuzzleBoxTheme {
        Box(Modifier.size(360.dp, 640.dp)) {
            TokenCatalogScreen()
        }
    }
}

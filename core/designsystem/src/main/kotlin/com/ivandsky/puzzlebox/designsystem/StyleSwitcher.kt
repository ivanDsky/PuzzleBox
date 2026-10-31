package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The ticket 08 debug switcher UI: typography and palette flipped independently, one
 * chip row per axis. Pure presentation — callers own persistence; the section restyles
 * itself through MaterialTheme, so it demos the active combo while you flip it.
 *
 * The catalog renders it behind a debug-only slot; a release build passes null and no
 * switcher entry point exists.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleSwitcherSection(
    typographyVariant: TypographyVariant,
    paletteVariant: PaletteVariant,
    onTypographyVariant: (TypographyVariant) -> Unit,
    onPaletteVariant: (PaletteVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "Style prototypes",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Debug only — flip each axis live; your pick survives restart. Three typography × three palette, every combo valid.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "Typography",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TypographyVariant.entries.forEach { variant ->
                    FilterChip(
                        selected = variant == typographyVariant,
                        onClick = { onTypographyVariant(variant) },
                        label = { Text(variant.label) },
                    )
                }
            }
            Text(
                text = typographyVariant.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "Palette",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PaletteVariant.entries.forEach { variant ->
                    FilterChip(
                        selected = variant == paletteVariant,
                        onClick = { onPaletteVariant(variant) },
                        label = { Text(variant.label) },
                    )
                }
            }
            Text(
                text = paletteVariant.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

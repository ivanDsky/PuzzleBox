package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// Status read-outs during play. Pills on container colors so they read at a glance;
// both container/on pairs are guarded by the WCAG AA test in ColorTokensTest.
@Composable
fun ScoreChip(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    ChipSurface(modifier, containerColor = scheme.primaryContainer) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onPrimaryContainer,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onPrimaryContainer,
        )
    }
}

@Composable
fun HintChip(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    ChipSurface(modifier, containerColor = scheme.tertiaryContainer) {
        Text(
            text = if (count == 1) "1 hint" else "$count hints",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun ChipSurface(
    modifier: Modifier,
    containerColor: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Catalog demo: score and hint pills as they read during play. */
@Composable
internal fun ChipsCatalogSection() {
    CatalogSection(title = "Chips") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ScoreChip(value = "1:24", label = "Score")
            HintChip(count = 3)
        }
    }
}

// Previews — Studio tooling only, zero public API surface (internal, not private, so
// Showkase's generated index can access them; ticket 00 visibility findings). Exactly
// light + dark, always inside PuzzleBoxTheme (spec A1/A5). A8: neither chip animates —
// no infinite transition, no trigger — so static panes show the resting state.

@ShowkaseComposable(name = "ScoreChip", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun ScoreChipPreview() {
    PuzzleBoxTheme {
        ScoreChip(value = "1:24", label = "Score")
    }
}

@ShowkaseComposable(name = "HintChip", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun HintChipPreview() {
    PuzzleBoxTheme {
        HintChip(count = 3)
    }
}

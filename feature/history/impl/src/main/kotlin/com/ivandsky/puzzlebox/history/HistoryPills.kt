package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.gameui.GameBrand

/** The game's fastest numbered solve, in its accent (history.md §2). */
@Composable
internal fun BestPill(brand: GameBrand, modifier: Modifier = Modifier) {
    AccentPill(text = "Best", brand = brand, style = MaterialTheme.typography.labelSmall, modifier = modifier)
}

/**
 * A day opened after its own day (spec A21): a neutral tag, so it reads as provenance
 * rather than an achievement next to the accent pills.
 */
@Composable
internal fun ArchivePill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        PillText(text = "From the archive", style = MaterialTheme.typography.labelSmall)
    }
}

/** A small tag on [brand]'s accent container: "Best", or "Keep going" on an unfinished row. */
@Composable
internal fun AccentPill(text: String, brand: GameBrand, style: TextStyle, modifier: Modifier = Modifier) {
    val accent = brand.accent.colors()
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = accent.container,
        contentColor = accent.onContainer,
    ) {
        PillText(text = text, style = style)
    }
}

@Composable
private fun PillText(text: String, style: TextStyle) {
    Text(
        text = text,
        style = style,
        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs / 2),
    )
}

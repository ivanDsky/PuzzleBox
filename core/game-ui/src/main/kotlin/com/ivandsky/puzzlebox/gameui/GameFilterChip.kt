package com.ivandsky.puzzlebox.gameui

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * One selectable chip: the game's inline glyph, or [icon] in its place, plus [label]. Callers own their row, any "All" chip and
 * test tags.
 */
@Composable
fun GameFilterChip(
    brand: GameBrand,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = icon ?: { GameGlyph(brand, GameGlyphSize.Inline) },
    )
}

package com.ivandsky.puzzlebox.gameui

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** One selectable game chip: the game's inline glyph plus [label]. Callers own their row, any "All" chip and test tags. */
@Composable
fun GameFilterChip(
    brand: GameBrand,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        leadingIcon = { GameGlyph(brand, GameGlyphSize.Inline) },
    )
}

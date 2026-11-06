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

/** The game's best numbered solve under its ranking, in its accent (history.md §2). */
@Composable
internal fun BestPill(brand: GameBrand, modifier: Modifier = Modifier) {
    AccentPill(text = "Best", brand = brand, style = MaterialTheme.typography.labelSmall, modifier = modifier)
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
        Text(
            text = text,
            style = style,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs / 2),
        )
    }
}

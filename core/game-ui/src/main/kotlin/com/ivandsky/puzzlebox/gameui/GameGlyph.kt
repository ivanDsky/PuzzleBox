package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.LocalDesignVariants
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors

/**
 * A game's glyph in its accent (identity §2.5): outlined in the accent on the accent
 * container, or, when [solid], in onAccent (white in light) on the accent itself.
 * [solid] defaults to the glyph style in [LocalDesignVariants] (outlined in release).
 * [GameGlyphSize.Inline] draws the bare glyph in the accent, with no well.
 */
@Composable
fun GameGlyph(
    brand: GameBrand,
    size: GameGlyphSize,
    modifier: Modifier = Modifier,
    solid: Boolean = LocalDesignVariants.current.glyphSolid,
) {
    val accent = brand.accent.colors()
    if (size.box == size.glyph) {
        Icon(brand.glyph, contentDescription = null, modifier = modifier.size(size.glyph), tint = accent.accent)
        return
    }
    Box(
        modifier = modifier
            .size(size.box)
            .background(if (solid) accent.accent else accent.container, RoundedCornerShape(size.corner)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = brand.glyph,
            contentDescription = null,
            modifier = Modifier.size(size.glyph),
            tint = if (solid) accent.onAccent else accent.accent,
        )
    }
}

// Preview: every size in both styles (tinted, then solid; Inline → Hero), for the
// neutral brand and one accent slot.
@PuzzleBoxPreviews
@Composable
internal fun GameGlyphPreview() {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(GameBrand.Neutral, GameBrand.Neutral.copy(accent = AccentSlot.Teal)).forEach { brand ->
                listOf(false, true).forEach { solid ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GameGlyphSize.entries.forEach { size -> GameGlyph(brand, size, solid = solid) }
                    }
                }
            }
        }
    }
}

package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.gameGlyph

/** Strands' glyph: a traced word, six letter beads joined by one winding stroke, on the 24 grid. */
internal val StrandsGlyph: ImageVector = gameGlyph(
    name = "Strands",
    stroke = "M5.5 18.5L11 15L7 10L12.5 5.5L18.5 8.5L17.5 15",
    fill = "M4.25 18.5a1.25 1.25 0 1 0 2.5 0a1.25 1.25 0 1 0 -2.5 0" +
        "M10 15a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" +
        "M6 10a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" +
        "M11.5 5.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" +
        "M17.5 8.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" +
        "M16.25 15a1.25 1.25 0 1 0 2.5 0a1.25 1.25 0 1 0 -2.5 0",
)

// Strands' glyph at every GameGlyphSize, tinted then solid, in light and dark.
@PuzzleBoxPreviews
@Composable
internal fun StrandsGlyphPreview() {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(false, true).forEach { solid ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameGlyphSize.entries.forEach { size -> GameGlyph(StrandsBrand, size, solid = solid) }
                }
            }
        }
    }
}

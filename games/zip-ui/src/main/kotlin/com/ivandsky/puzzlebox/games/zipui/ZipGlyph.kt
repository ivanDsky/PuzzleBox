package com.ivandsky.puzzlebox.games.zipui

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

/** Zip's glyph (identity §2.5): the snake path, a filled start dot and an open end dot, on the 24 grid. */
internal val ZipGlyph: ImageVector = gameGlyph(
    name = "Zip",
    stroke = "M5 6H19V12H5V18H17M16.5 18a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
    fill = "M2.5 6a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
)

// Zip's glyph at every GameGlyphSize, tinted then solid, in light and dark.
@PuzzleBoxPreviews
@Composable
internal fun ZipGlyphPreview() {
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
                    GameGlyphSize.entries.forEach { size -> GameGlyph(ZipBrand, size, solid = solid) }
                }
            }
        }
    }
}

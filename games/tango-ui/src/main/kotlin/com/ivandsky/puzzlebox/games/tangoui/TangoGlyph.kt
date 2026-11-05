package com.ivandsky.puzzlebox.games.tangoui

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

/** Tango's glyph (identity §2.5): a filled sun disc beside a stroked crescent moon, no rays, on the 24 grid. */
internal val TangoGlyph: ImageVector = gameGlyph(
    name = "Tango",
    stroke = "M19.5 6.8A5.5 5.5 0 1 0 19.5 17.2A6 6 0 0 1 19.5 6.8Z",
    fill = "M3.5 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
)

// Tango's glyph at every GameGlyphSize, tinted then solid, in light and dark.
@PuzzleBoxPreviews
@Composable
internal fun TangoGlyphPreview() {
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
                    GameGlyphSize.entries.forEach { size -> GameGlyph(TangoBrand, size, solid = solid) }
                }
            }
        }
    }
}

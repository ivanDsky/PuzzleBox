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

/** Tango's glyph (identity §2.5): the board's sun, a filled disc with eight short rays, on the 24 grid. */
internal val TangoGlyph: ImageVector = gameGlyph(
    name = "Tango",
    stroke = "M12 2.5V4.5M12 19.5V21.5M2.5 12H4.5M19.5 12H21.5" +
        "M5.28 5.28L6.7 6.7M17.3 17.3L18.72 18.72M5.28 18.72L6.7 17.3M17.3 6.7L18.72 5.28",
    fill = "M8.5 12a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0",
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

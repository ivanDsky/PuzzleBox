package com.ivandsky.puzzlebox.games.sudokuui

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

/** Mini Sudoku's glyph (hub-card prototype A1): a rounded 3×3 grid with a filled centre cell, on the 24 grid. */
internal val MiniSudokuGlyph: ImageVector = gameGlyph(
    name = "MiniSudoku",
    stroke = "M8 4H16A4 4 0 0 1 20 8V16A4 4 0 0 1 16 20H8A4 4 0 0 1 4 16V8A4 4 0 0 1 8 4Z" +
        "M9.33 4V20M14.67 4V20M4 9.33H20M4 14.67H20",
    fill = "M10.33 10.33H13.67V13.67H10.33Z",
)

// Mini Sudoku's glyph at every GameGlyphSize, tinted then solid, in light and dark.
@PuzzleBoxPreviews
@Composable
internal fun MiniSudokuGlyphPreview() {
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
                    GameGlyphSize.entries.forEach { size -> GameGlyph(MiniSudokuBrand, size, solid = solid) }
                }
            }
        }
    }
}

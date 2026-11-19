package com.ivandsky.puzzlebox.resultui

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GameBrand

// Previews of GameFilterChip — internal, light + dark inside PuzzleBoxTheme: selected and unselected.

private val PreviewBrand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)

@PuzzleBoxPreviews
@Composable
internal fun GameFilterChipSelectedPreview() {
    PuzzleBoxTheme { GameFilterChip(PreviewBrand, "Queens 3", selected = true, onClick = {}) }
}

@PuzzleBoxPreviews
@Composable
internal fun GameFilterChipUnselectedPreview() {
    PuzzleBoxTheme { GameFilterChip(PreviewBrand, "Queens 3", selected = false, onClick = {}) }
}

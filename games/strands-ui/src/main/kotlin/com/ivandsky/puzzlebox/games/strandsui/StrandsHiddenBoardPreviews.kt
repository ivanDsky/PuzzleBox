package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoard

/**
 * Strands' board before play as a puzzle's page shows it, light and dark: hidden, and plain for whoever may see
 * it. The board is the daily shape, 8 rows of 6, taller than it is wide. The puzzle travels with its words, as a
 * shared one does; [StrandsReplayBoard.givens] leaves them behind and keeps the letters.
 */
private val previewEnvelope: Puzzle by lazy {
    val puzzle = StrandsPuzzle(
        rows = 8,
        cols = 6,
        letters = "BAKERYUNSOPIRCAKETTARTSELOAFSDSCONEEPIESHTOASTSM",
        clue = "Bake sale",
        spangram = StrandsWord("BURTLSEH", List(8) { Coord(it, 0) }),
        themeWords = listOf(StrandsWord("BAKERY", List(6) { Coord(0, it) })),
        validWords = setOf("BURTLSEH", "BAKERY", "CAKE"),
        constructors = "PuzzleBox",
    )
    StrandsCodec.envelope(puzzle)
}

@PuzzleBoxPreviews
@Composable
internal fun StrandsHiddenBoardBlurredPreview() = HiddenBoardPane(sharp = false)

@PuzzleBoxPreviews
@Composable
internal fun StrandsHiddenBoardSharpPreview() = HiddenBoardPane(sharp = true)

@Composable
private fun HiddenBoardPane(sharp: Boolean) {
    val board = StrandsReplayBoard.givens(previewEnvelope) ?: return
    PuzzleBoxTheme { Surface { HiddenBoard(board, sharp = sharp, modifier = Modifier.padding(Spacing.lg)) } }
}

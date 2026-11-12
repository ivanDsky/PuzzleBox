package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.BestScore

// Previews of ResultScreen (a friend puzzle's result) — internal, light + dark inside
// PuzzleBoxTheme: a record solve (the pill) and a play-again (its line).

private val PreviewBrand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)

@PuzzleBoxPreviews
@Composable
internal fun ResultScreenNewBestPreview() {
    PuzzleBoxTheme {
        ResultScreen(
            timeMs = 58_000,
            hintsUsed = 0,
            onDone = {},
            onShare = {},
            onPlayAgain = {},
            brand = PreviewBrand,
            game = "Queens",
            verdict = RecordVerdict.NewBest,
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultScreenPlayAgainPreview() {
    PuzzleBoxTheme {
        ResultScreen(
            timeMs = 95_000,
            hintsUsed = 0,
            onDone = {},
            onShare = {},
            onPlayAgain = {},
            brand = PreviewBrand,
            game = "Queens",
            verdict = RecordVerdict.PlayAgain,
            previousBest = BestScore(88_000, hints = 0),
        )
    }
}

package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant

// Previews of ResultScreen — internal, light + dark inside PuzzleBoxTheme: today's live
// daily (new best, Next), a slower solve with everything done (the countdown), a
// practice puzzle (no pill, no streak), and a replay (Done).

private val PreviewBrand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)

/** Seven hours and twelve minutes from the preview's wall clock. */
private fun previewRollover(): Instant = Instant.now().plusSeconds(7 * 3600L + 12 * 60L)

@PuzzleBoxPreviews
@Composable
internal fun ResultScreenNewBestPreview() {
    PuzzleBoxTheme {
        ResultScreen(
            timeMs = 58_000,
            hintsUsed = 0,
            primary = ResultPrimaryAction.Next(GameId("tango"), "Tango"),
            onPrimary = {},
            onShare = {},
            onPlayAgain = {},
            brand = PreviewBrand,
            game = "Queens",
            puzzleNumber = 918,
            verdict = RecordVerdict.NewBest,
            previousBest = BestScore(65_000, hints = 0),
            streak = 3,
            doneToday = 2,
            total = 3,
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultScreenAllDonePreview() {
    PuzzleBoxTheme {
        ResultScreen(
            timeMs = 95_000,
            hintsUsed = 2,
            primary = ResultPrimaryAction.AllDone,
            onPrimary = {},
            onShare = {},
            onPlayAgain = {},
            brand = PreviewBrand,
            game = "Queens",
            puzzleNumber = 918,
            verdict = RecordVerdict.NotBest,
            previousBest = BestScore(46_000, hints = 0),
            streak = 1,
            doneToday = 3,
            total = 3,
            rolloverAt = previewRollover(),
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultScreenPracticePreview() {
    PuzzleBoxTheme {
        ResultScreen(
            timeMs = 72_000,
            hintsUsed = 1,
            primary = ResultPrimaryAction.Next(GameId("zip"), "Zip"),
            onPrimary = {},
            onShare = {},
            onPlayAgain = {},
            brand = PreviewBrand,
            game = "Tango",
            practice = true,
            streak = 3,
            doneToday = 1,
            total = 3,
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultScreenReplayPreview() {
    PuzzleBoxTheme {
        ResultScreen(
            timeMs = 95_000,
            hintsUsed = 0,
            primary = ResultPrimaryAction.Done,
            onPrimary = {},
            onShare = {},
            onPlayAgain = {},
            brand = PreviewBrand,
            game = "Queens",
            puzzleNumber = 917,
            verdict = RecordVerdict.Replay,
            previousBest = BestScore(88_000, hints = 0),
            streak = 3,
            doneToday = 1,
            total = 3,
        )
    }
}

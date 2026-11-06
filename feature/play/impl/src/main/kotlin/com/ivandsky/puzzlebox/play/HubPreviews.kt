package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId

// Previews of the hub in the hub-card handoff's states (§States) over five stand-in games
// that carry only a hue; the glyph is the neutral square.

private val Time = RankOrder.TimeThenHints
private val Hints = RankOrder.HintsThenTime

/** The two-pane preview's width: a 900dp tablet, as the handoff measures it. */
private val TabletWidth = 900.dp

private val zip = GameId("zip")
private val queens = GameId("queens")
private val tango = GameId("tango")
private val sudoku = GameId("sudoku")
private val strands = GameId("strands")

private fun brand(accent: AccentSlot) = GameBrand.Neutral.copy(accent = accent)

private fun row(game: GameId, status: TodayStatus?, number: Int? = null, best: BestScore? = null, line: String? = null) =
    when (game) {
        zip -> HubRow(zip, "Zip", Time, brand(AccentSlot.Blue), number, status, best, line)
        queens -> HubRow(queens, "Queens", Time, brand(AccentSlot.Violet), number, status, best, line)
        tango -> HubRow(tango, "Tango", Time, brand(AccentSlot.Orange), number, status, best, line)
        sudoku -> HubRow(sudoku, "Mini Sudoku", Time, brand(AccentSlot.Teal), number, status, best, line)
        else -> HubRow(game, "Strands", Hints, brand(AccentSlot.Pink), number, status, best, line)
    }

/** Zip solved, Queens in progress and up next, Tango new, Mini Sudoku offline, Strands loading. */
private val mixedState = HubUiState(
    rows = listOf(
        row(zip, TodayStatus.Solved(42_000L), 592, BestScore(31_000L, 0)),
        row(queens, TodayStatus.InProgress(192_000L), 411, BestScore(95_000L, 0), line = "8×8 · 5 of 8 queens"),
        row(tango, TodayStatus.NotStarted, 431),
        row(sudoku, TodayStatus.Offline, best = BestScore(64_000L, 0)),
        row(strands, null),
    ),
    doneCount = 1,
    next = queens,
)

private val allSolvedState = HubUiState(
    rows = listOf(
        row(zip, TodayStatus.Solved(42_000L), 592, BestScore(31_000L, 0)),
        row(queens, TodayStatus.Solved(95_000L), 411, BestScore(95_000L, 0)),
        row(tango, TodayStatus.Solved(58_000L), 431, BestScore(50_000L, 0)),
        row(sudoku, TodayStatus.Solved(64_000L), 77, BestScore(64_000L, 0)),
        row(strands, TodayStatus.Solved(310_000L), 210, BestScore(300_000L, 0)),
    ),
    doneCount = 5,
)

private val loadingState = HubUiState(rows = listOf(zip, queens, tango, sudoku, strands).map { row(it, null) })

/** Zip solved; every other game can't load, so nothing is up next. */
private val restOfflineState = HubUiState(
    rows = listOf(
        row(zip, TodayStatus.Solved(42_000L), 592, BestScore(31_000L, 0)),
        row(queens, TodayStatus.Offline, best = BestScore(95_000L, 0)),
        row(tango, TodayStatus.Offline),
        row(sudoku, TodayStatus.Offline),
        row(strands, TodayStatus.Offline),
    ),
    doneCount = 1,
)

/** Four-digit numbers, hour-long times, two offline tiles and the longest name. */
private val longestState = HubUiState(
    rows = listOf(
        row(zip, TodayStatus.InProgress(3_723_000L), 1234, BestScore(3_599_000L, 0), line = "9×9 · 1 of 81 cells"),
        row(queens, TodayStatus.Solved(754_000L), 1234, BestScore(700_000L, 0)),
        row(tango, TodayStatus.Offline, best = BestScore(3_599_000L, 0)),
        row(sudoku, TodayStatus.Offline, best = BestScore(64_000L, 0)),
        row(strands, TodayStatus.InProgress(3_599_000L), 1234, BestScore(3_000_000L, 2), line = "6 of 8 words"),
    ),
    doneCount = 1,
    next = zip,
)

@PuzzleBoxPreviews
@Composable
private fun HubPhonePreview() {
    PuzzleBoxTheme {
        GamePicker(state = mixedState, layout = PlayLayout.SinglePane, onPick = {}, onOpenHistory = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun HubPhoneAllSolvedPreview() {
    PuzzleBoxTheme {
        GamePicker(state = allSolvedState, layout = PlayLayout.SinglePane, onPick = {}, onOpenHistory = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun HubPhoneLoadingPreview() {
    PuzzleBoxTheme {
        GamePicker(state = loadingState, layout = PlayLayout.SinglePane, onPick = {}, onOpenHistory = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun HubPhoneRestOfflinePreview() {
    PuzzleBoxTheme {
        GamePicker(state = restOfflineState, layout = PlayLayout.SinglePane, onPick = {}, onOpenHistory = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun HubPhoneLongestPreview() {
    PuzzleBoxTheme {
        GamePicker(state = longestState, layout = PlayLayout.SinglePane, onPick = {}, onOpenHistory = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun HubTabletPreview() {
    PuzzleBoxTheme {
        GamePicker(
            state = mixedState,
            layout = PlayLayout.TwoPane,
            onPick = {},
            onOpenHistory = {},
            modifier = Modifier.width(TabletWidth),
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun HubTabletAllSolvedPreview() {
    PuzzleBoxTheme {
        GamePicker(
            state = allSolvedState,
            layout = PlayLayout.TwoPane,
            onPick = {},
            onOpenHistory = {},
            modifier = Modifier.width(TabletWidth),
        )
    }
}

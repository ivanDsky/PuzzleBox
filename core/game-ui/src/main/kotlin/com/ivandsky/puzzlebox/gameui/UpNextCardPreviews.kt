package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId

private fun upNext(
    status: TodayStatus?,
    number: Int? = 411,
    best: BestScore? = BestScore(timeMs = 95_000L, hints = 0),
    line: String? = null,
    accent: AccentSlot = AccentSlot.Violet,
) = GameCardModel(
    game = GameId("preview"),
    name = "Mini Sudoku",
    brand = GameBrand.Neutral.copy(accent = accent),
    status = status,
    rankOrder = RankOrder.TimeThenHints,
    puzzleNumber = number,
    best = best,
    line = line,
)

private val PhoneStates = listOf(
    upNext(TodayStatus.NotStarted, best = null),
    upNext(TodayStatus.InProgress(192_000L), line = "6×6 · #411", accent = AccentSlot.Teal),
    upNext(null, accent = AccentSlot.Pink),
    upNext(TodayStatus.InProgress(3_723_000L), number = 1234, best = BestScore(3_599_000L, 0), line = "12 of 14 words", accent = AccentSlot.Orange),
)

// Preview: phone hero (380dp content) per state: new, in progress + line, loading, longest h:mm:ss + #1234.
@PuzzleBoxPreviews
@Composable
private fun UpNextCardPhonePreview() {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).width(412.dp).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PhoneStates.forEach { UpNextCard(model = it, tall = false, onClick = {}) }
        }
    }
}

// Preview: tablet hero (5-weight column, ~373dp) without and with a line.
@PuzzleBoxPreviews
@Composable
private fun UpNextCardTabletPreview() {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).width(405.dp).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            UpNextCard(model = upNext(TodayStatus.InProgress(192_000L)), tall = true, onClick = {})
            UpNextCard(model = PhoneStates.last(), tall = true, onClick = {})
        }
    }
}

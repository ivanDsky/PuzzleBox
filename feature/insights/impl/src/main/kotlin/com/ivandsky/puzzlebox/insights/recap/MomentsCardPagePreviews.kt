package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment.Kind
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

// The Moments page, its sticker sheet, plaque and tablet list fact, light and dark: a month with every kind, and a month with one. A preview never animates, so the stickers are all placed.

private val Queens = GameId("queens")

private fun october(day: Int) = LocalDate.of(2026, 10, day)

private val Month = RecapCard.Moments(
    listOf(
        EarnedMoment(Kind.PersonalBest, october(9), Queens),
        EarnedMoment(Kind.FullSet, october(11)),
        EarnedMoment(Kind.Streak, october(12), streakDays = 7),
        EarnedMoment(Kind.FirstSubX, october(22), Queens, thresholdMs = 60_000),
    ),
)

private val One = RecapCard.Moments(listOf(Month.earned.first()))

private fun name(game: GameId) = "Queens"

@PuzzleBoxPreviews
@Composable
private fun MomentsCardPagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { MomentsCardPage(Month, "October 2026", ::name, initialPeeled = 3) }
}

@PuzzleBoxPreviews
@Composable
private fun MomentsCardPageOnePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { MomentsCardPage(One, "October 2026", ::name) }
}

@PuzzleBoxPreviews
@Composable
private fun MomentStickerSheetPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        MomentStickerSheet(Month.earned, ::name, placed = 4, peeled = 1, onPeel = {}, modifier = Modifier.fillMaxWidth().height(260.dp))
    }
}

@PuzzleBoxPreviews
@Composable
private fun MomentPlaquePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { MomentPlaque(Month.earned.last(), Month.earned, ::name) }
    }
}

@PuzzleBoxPreviews
@Composable
private fun MomentsCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { MomentsCardFact(Month, ::name) }
    }
}

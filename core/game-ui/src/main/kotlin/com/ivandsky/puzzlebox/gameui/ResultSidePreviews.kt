package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

// Previews of ResultSideCard and TimeSpreadChart — internal, light + dark inside PuzzleBoxTheme,
// one per state: a plain solve, a first solve (ghost bars), a new best, a hints-first (Strands)
// solve, signed out, no rank row yet, a replay (spread only), and the long case (a 365-day
// streak, long names, a thin column). The sample builders are the hero card's.

private val SideBrand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)
private val SideStrandsBrand = GameBrand(AccentSlot.Teal, GameGlyphs.Queens)

@Composable
private fun SidePreview(model: ResultHeroModel, brand: GameBrand = SideBrand, width: Int = 360, wide: Boolean = false) {
    PuzzleBoxTheme {
        Box(Modifier.width(width.dp).padding(Spacing.lg)) {
            ResultSideCard(model = model, brand = brand, onSignIn = {}, wide = wide)
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideDefaultPreview() {
    SidePreview(heroSampleModel(verdict = RecordVerdict.NotBest, timeMs = 100_000L))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideFirstSolvePreview() {
    SidePreview(
        heroSampleModel(verdict = RecordVerdict.FirstSolve, previous = null, earlier = emptyList(), streak = 1, doneToday = 1),
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideNewBestPreview() {
    SidePreview(heroSampleModel(verdict = RecordVerdict.NewBest, timeMs = 58_000L, previous = BestScore(65_000L, 0)))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideHintsFirstPreview() {
    SidePreview(
        model = heroSampleModel(
            verdict = RecordVerdict.NewBest,
            order = RankOrder.HintsThenTime,
            game = "Strands",
            timeMs = 142_000L,
            previous = BestScore(150_000L, 2),
        ),
        brand = SideStrandsBrand,
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideSignedOutPreview() {
    SidePreview(heroSampleModel(rank = ResultRank.SignedOut))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideConnectionsRankPreview() {
    SidePreview(
        heroSampleModel(
            rank = ResultRank.Ranked(position = 14, count = 212, topPercent = 7, withConnections = ResultRank.WithConnections(3, 12)),
        ),
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideNoRankPreview() {
    SidePreview(heroSampleModel(rank = ResultRank.Hidden))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideReplayPreview() {
    SidePreview(heroSampleModel(verdict = RecordVerdict.Replay, replay = true))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideLongPreview() {
    SidePreview(
        model = heroSampleModel(
            verdict = RecordVerdict.NotBest,
            streak = 365,
            game = "A very long game name that keeps going",
            timeMs = 3_723_000L,
            earlier = listOf(BestScore(3_000_000L, 0), BestScore(3_400_000L, 3)),
            rank = ResultRank.Ranked(position = 12_345, count = 98_765, topPercent = 13),
        ),
        width = 320,
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideWidePreview() {
    SidePreview(heroSampleModel(verdict = RecordVerdict.NotBest), width = 520, wide = true)
}

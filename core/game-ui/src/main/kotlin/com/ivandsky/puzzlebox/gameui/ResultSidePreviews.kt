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
import com.ivandsky.puzzlebox.domain.SpreadScope
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

// Previews of ResultSideCard and TimeSpreadChart — internal, light + dark inside PuzzleBoxTheme,
// one per state: a plain solve, a first solve, 2/3/4/10/60 solves, a new best, the slowest,
// hints-first (Strands), one hour plus, a replay, the weekday scope with 0/1/3/8 earlier solves,
// the fastest weekday, signed out, no rank row yet, and the long case (a 365-day streak, long
// names, a thin column). The sample builders are the hero card's. A chart with no weekday does not
// exist here: practice puzzles, the only ones without one, draw no spread.

private val SideBrand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)
private val SideStrandsBrand = GameBrand(AccentSlot.Teal, GameGlyphs.Queens)

@Composable
private fun SidePreview(
    model: ResultHeroModel,
    brand: GameBrand = SideBrand,
    width: Int = 360,
    wide: Boolean = false,
    initialScope: SpreadScope? = null,
) {
    PuzzleBoxTheme {
        Box(Modifier.width(width.dp).padding(Spacing.lg)) {
            ResultSideCard(model = model, brand = brand, onSignIn = {}, wide = wide, initialScope = initialScope)
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

/** [count] deterministic earlier solves spread between 1:10 and 2:39. */
private fun earlierTimes(count: Int): List<BestScore> = List(count) { BestScore(70_000L + ((it * 37) % 90) * 1000L, 0) }

@PuzzleBoxPreviews
@Composable
internal fun ResultSideTwoSolvesPreview() {
    SidePreview(heroSampleModel(earlier = earlierTimes(1)))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideThreeSolvesPreview() {
    SidePreview(heroSampleModel(earlier = earlierTimes(2)))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideFourSolvesPreview() {
    SidePreview(heroSampleModel(earlier = earlierTimes(3)))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideTenSolvesPreview() {
    SidePreview(heroSampleModel(earlier = earlierTimes(9)))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideSixtySolvesPreview() {
    SidePreview(heroSampleModel(earlier = earlierTimes(59)))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideSlowestPreview() {
    SidePreview(heroSampleModel(timeMs = 150_000L))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideHourPlusPreview() {
    SidePreview(
        heroSampleModel(
            timeMs = 3_723_000L,
            earlier = listOf(BestScore(3_600_000L, 0), BestScore(4_100_000L, 0), BestScore(3_900_000L, 0)),
        ),
        width = 320,
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideWeekdayNoEarlierPreview() {
    SidePreview(heroSampleModel(weekdayEarlier = emptyList()), initialScope = SpreadScope.Weekday)
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideWeekdayOneEarlierPreview() {
    SidePreview(heroSampleModel(weekdayEarlier = earlierTimes(1)), initialScope = SpreadScope.Weekday)
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideWeekdayThreeEarlierPreview() {
    SidePreview(heroSampleModel(weekdayEarlier = earlierTimes(3)))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideWeekdayEightEarlierPreview() {
    SidePreview(heroSampleModel(earlier = earlierTimes(20), weekdayEarlier = earlierTimes(8)))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideFastestWeekdayPreview() {
    SidePreview(
        heroSampleModel(
            timeMs = 80_000L,
            earlier = earlierTimes(12),
            weekdayEarlier = listOf(BestScore(82_000L, 0), BestScore(85_000L, 0), BestScore(90_000L, 0)),
            fastestWeekday = true,
        ),
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ResultSideReplayWeekdayPreview() {
    SidePreview(heroSampleModel(verdict = RecordVerdict.Replay, replay = true, weekdayEarlier = earlierTimes(3)))
}

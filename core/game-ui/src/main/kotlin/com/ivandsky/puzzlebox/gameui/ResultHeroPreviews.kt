package com.ivandsky.puzzlebox.gameui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ResultDay
import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.timeSpread
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

// Previews of ResultHeroCard — internal, light + dark inside PuzzleBoxTheme, one per state:
// first solve, a plain solve, a new best, a hints-first (Strands) new best, the long case
// (1:02:03, 12 hints, long names) at a phone and at the wide column, and all done. The
// sample builders are shared with the tests and the side card's previews.

private val PreviewBrand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)
private val StrandsBrand = GameBrand(AccentSlot.Teal, GameGlyphs.Queens)

private val SampleToday: LocalDate = LocalDate.of(2026, 11, 7)
private val SampleEarlier = listOf(112_000L, 90_000L, 104_000L, 98_000L, 121_000L).map { BestScore(it, 0) }

/** The insights of one sample solve: a week ending Saturday 7 Nov with the last three days solved. */
internal fun heroSampleInsights(
    current: BestScore,
    earlier: List<BestScore> = SampleEarlier,
    order: RankOrder = RankOrder.TimeThenHints,
    streak: Int = 3,
) = ResultInsights(
    streak = streak,
    week = (6 downTo 0).map { ResultDay(SampleToday.minusDays(it.toLong()), solved = it < streak.coerceAtMost(7), today = it == 0) },
    spread = timeSpread(earlier, current, order),
)

/** The facts of one sample Results page; every argument is one knob of a preview or a test. */
internal fun heroSampleFacts(
    verdict: RecordVerdict? = RecordVerdict.NotBest,
    timeMs: Long = 100_000L,
    previous: BestScore? = BestScore(90_000L, 0),
    earlier: List<BestScore> = SampleEarlier,
    order: RankOrder = RankOrder.TimeThenHints,
    hintsUsed: Int = 0,
    game: String = "Queens",
    number: Int? = 918,
    practice: Boolean = false,
    replay: Boolean = false,
    streak: Int = 3,
    rank: ResultRank = ResultRank.Ranked(position = 14, count = 212, topPercent = 7),
    doneToday: Int? = 2,
    total: Int? = 3,
    primary: ResultPrimaryAction? = ResultPrimaryAction.Next(GameId("tango"), "Tango"),
) = ResultHeroFacts(
    timeMs = timeMs,
    hintsUsed = hintsUsed,
    verdict = verdict,
    previousBest = previous,
    rankOrder = order,
    game = game,
    puzzleNumber = number,
    practice = practice,
    replay = replay,
    dateLine = "Today · Sat 7 Nov",
    doneToday = doneToday,
    total = total,
    insights = heroSampleInsights(BestScore(timeMs, hintsUsed), earlier, order, streak),
    rank = rank,
    primary = primary,
)

/** The model of [heroSampleFacts] for the same arguments, in US English so weekday initials are stable. */
internal fun heroSampleModel(
    verdict: RecordVerdict? = RecordVerdict.NotBest,
    timeMs: Long = 100_000L,
    previous: BestScore? = BestScore(90_000L, 0),
    earlier: List<BestScore> = SampleEarlier,
    order: RankOrder = RankOrder.TimeThenHints,
    hintsUsed: Int = 0,
    game: String = "Queens",
    number: Int? = 918,
    practice: Boolean = false,
    replay: Boolean = false,
    streak: Int = 3,
    rank: ResultRank = ResultRank.Ranked(position = 14, count = 212, topPercent = 7),
    doneToday: Int? = 2,
    total: Int? = 3,
    primary: ResultPrimaryAction? = ResultPrimaryAction.Next(GameId("tango"), "Tango"),
): ResultHeroModel = resultHeroModel(
    heroSampleFacts(
        verdict, timeMs, previous, earlier, order, hintsUsed, game, number, practice, replay,
        streak, rank, doneToday, total, primary,
    ),
    Locale.US,
)

/** Seven hours and twelve minutes from the preview's wall clock. */
private fun previewRollover(): Instant = Instant.now().plusSeconds(7 * 3600L + 12 * 60L)

@Composable
private fun HeroPreview(
    model: ResultHeroModel,
    brand: GameBrand = PreviewBrand,
    wide: Boolean = false,
    width: Int = 360,
    rolloverAt: Instant? = null,
) {
    PuzzleBoxTheme {
        Box(Modifier.width(width.dp).padding(Spacing.lg)) {
            ResultHeroCard(
                model = model,
                brand = brand,
                onPrimary = {},
                onShare = {},
                onPlayAgain = {},
                rolloverAt = rolloverAt,
                wide = wide,
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultHeroFirstSolvePreview() {
    HeroPreview(
        heroSampleModel(verdict = RecordVerdict.FirstSolve, previous = null, earlier = emptyList(), streak = 1, doneToday = 1),
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ResultHeroDefaultPreview() {
    HeroPreview(heroSampleModel(verdict = RecordVerdict.NotBest, timeMs = 100_000L, previous = BestScore(90_000L, 0)))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultHeroNewBestPreview() {
    HeroPreview(heroSampleModel(verdict = RecordVerdict.NewBest, timeMs = 58_000L, previous = BestScore(65_000L, 0)))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultHeroHintsFirstPreview() {
    HeroPreview(
        model = heroSampleModel(
            verdict = RecordVerdict.NewBest,
            order = RankOrder.HintsThenTime,
            game = "Strands",
            timeMs = 142_000L,
            hintsUsed = 0,
            previous = BestScore(150_000L, 2),
        ),
        brand = StrandsBrand,
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ResultHeroLongPreview() {
    HeroPreview(
        heroSampleModel(
            verdict = RecordVerdict.NotBest,
            timeMs = 3_723_000L,
            hintsUsed = 12,
            game = "A very long game name that keeps going",
            previous = BestScore(3_000_000L, 0),
            earlier = listOf(BestScore(3_000_000L, 0), BestScore(3_400_000L, 3)),
            doneToday = 10,
            total = 12,
            primary = ResultPrimaryAction.Next(GameId("long"), "The next game with a long name"),
        ),
        width = 320,
    )
}

@Preview(name = "Long wide", widthDp = 400, showBackground = true)
@Composable
internal fun ResultHeroLongWidePreview() {
    HeroPreview(
        heroSampleModel(
            verdict = RecordVerdict.NotBest,
            timeMs = 3_723_000L,
            hintsUsed = 12,
            game = "A very long game name that keeps going",
            previous = BestScore(3_000_000L, 0),
            earlier = listOf(BestScore(3_000_000L, 0), BestScore(3_400_000L, 3)),
        ),
        wide = true,
        width = 400,
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ResultHeroAllDonePreview() {
    HeroPreview(
        model = heroSampleModel(
            verdict = RecordVerdict.NotBest,
            timeMs = 95_000L,
            hintsUsed = 2,
            previous = BestScore(46_000L, 0),
            doneToday = 3,
            primary = ResultPrimaryAction.AllDone,
        ),
        rolloverAt = previewRollover(),
    )
}

private val ScreenNewBest = heroSampleFacts(verdict = RecordVerdict.NewBest, timeMs = 58_000L, previous = BestScore(65_000L, 0))

@Composable
private fun ScreenPreview(facts: ResultHeroFacts = ScreenNewBest) {
    PuzzleBoxTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ResultHeroScreen(
                facts = facts,
                onPrimary = {},
                onShare = {},
                onPlayAgain = {},
                onSignIn = {},
                onRetryWrite = {},
                brand = PreviewBrand,
                rolloverAt = previewRollover(),
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultHeroScreenPhonePreview() {
    PuzzleBoxTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ResultHeroScreen(
                facts = ScreenNewBest,
                onPrimary = {},
                onShare = {},
                onPlayAgain = {},
                onSignIn = {},
                onRetryWrite = {},
                brand = PreviewBrand,
                rolloverAt = previewRollover(),
            )
        }
    }
}

@Preview(name = "Tablet light", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Tablet dark", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun ResultHeroScreenTabletPreview() {
    ScreenPreview()
}

@Preview(name = "Tablet all done", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
internal fun ResultHeroScreenTabletAllDonePreview() {
    ScreenPreview(heroSampleFacts(doneToday = 3, primary = ResultPrimaryAction.AllDone))
}

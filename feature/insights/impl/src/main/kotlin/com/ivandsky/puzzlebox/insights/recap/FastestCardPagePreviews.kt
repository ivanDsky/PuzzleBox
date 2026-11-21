package com.ivandsky.puzzlebox.insights.recap

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

// The Fastest solve pages, one per game on its accent, light and dark, phone to wide tablet: a time with a replay, an
// imported one, a first solve, the widest times, a short page, no games; and the tablet's list fact.

private val Queens = GameId("queens")
private val Zip = GameId("zip")
private val Strands = GameId("strands")
private val Sudoku = GameId("sudoku")

private val Names = mapOf(Queens to "Queens", Zip to "Zip", Strands to "Strands", Sudoku to "Sudoku")

private fun name(game: GameId): String = Names.getValue(game)

private val Brands = mapOf(
    Queens to GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
    Zip to GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
    Strands to GameBrand(AccentSlot.Pink, GameGlyphs.Strands),
    Sudoku to GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku),
)

private fun nov(day: Int) = LocalDate.of(2026, 11, day)

private val Month = RecapCard.Fastest(
    rows = listOf(
        RecapCard.Fastest.Row(Queens, timeMs = 48_000, day = nov(17), puzzleNumber = 386, hasReplay = true, averageMs = 136_000, solves = 28),
        RecapCard.Fastest.Row(Zip, timeMs = 21_000, day = nov(3), puzzleNumber = null, hasReplay = false, averageMs = 58_000, solves = 22),
        RecapCard.Fastest.Row(Strands, timeMs = 160_000, day = nov(28), puzzleNumber = 320, hasReplay = true, averageMs = 277_000, solves = 8),
        RecapCard.Fastest.Row(Sudoku, timeMs = 312_000, day = nov(8), puzzleNumber = null, hasReplay = true, averageMs = null, solves = 1),
    ),
)

private val Longest = RecapCard.Fastest(listOf(Month.rows[1].copy(timeMs = 3_599_000, averageMs = 3_730_000, solves = 1_031)))

/** Game [sub]'s page on its accent, as the deck lays it: the field under the page. */
@Composable
private fun FastestPreviewPage(card: RecapCard.Fastest, sub: Int = 0) = PuzzleBoxTheme {
    CompositionLocalProvider(LocalGameBrands provides { Brands.getValue(it) }) {
        val field = card.rows.getOrNull(sub)?.let { Brands.getValue(it.game).accent.colors().accent } ?: MaterialTheme.colorScheme.background
        Box(Modifier.fillMaxSize().background(field)) {
            FastestCardPage(card, "November 2026", ::name, onWatchReplay = {}, sub = sub)
        }
    }
}

@Preview(name = "Phone Light", widthDp = 412, heightDp = 760, showBackground = true)
@Preview(name = "Phone Dark", widthDp = 412, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun FastestCardPagePreview() = FastestPreviewPage(Month)

@Preview(name = "Imported Light", widthDp = 412, heightDp = 760, showBackground = true)
@Preview(name = "Imported Dark", widthDp = 412, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun FastestCardPageImportedPreview() = FastestPreviewPage(Month, sub = 1)

@Preview(name = "Replay with puzzle", widthDp = 412, heightDp = 760, showBackground = true)
@Composable
private fun FastestCardPagePuzzlePreview() = FastestPreviewPage(Month, sub = 2)

@Preview(name = "First solve", widthDp = 412, heightDp = 760, showBackground = true)
@Composable
private fun FastestCardPageFirstSolvePreview() = FastestPreviewPage(Month, sub = 3)

@Preview(name = "Longest", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun FastestCardPageLongestPreview() = FastestPreviewPage(Longest)

@Preview(name = "Tight", widthDp = 640, heightDp = 320, showBackground = true)
@Composable
private fun FastestCardPageTightPreview() = FastestPreviewPage(Month)

@Preview(name = "Tablet", widthDp = 800, heightDp = 1180, showBackground = true)
@Composable
private fun FastestCardPageTabletPreview() = FastestPreviewPage(Month)

@Preview(name = "Tablet wide", widthDp = 1180, heightDp = 760, showBackground = true)
@Composable
private fun FastestCardPageWidePreview() = FastestPreviewPage(Month, sub = 2)

@PuzzleBoxPreviews
@Composable
private fun FastestCardPageEmptyPreview() = FastestPreviewPage(RecapCard.Fastest(emptyList()))

@PuzzleBoxPreviews
@Composable
private fun FastestCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { FastestCardFact(Month, ::name, onWatchReplay = {}) }
    }
}

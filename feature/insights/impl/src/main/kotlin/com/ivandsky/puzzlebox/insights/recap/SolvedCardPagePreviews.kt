package com.ivandsky.puzzlebox.insights.recap

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

// The Puzzles solved page and its tablet list fact, light and dark, on a phone, a two-pane tablet face (820×672) and the
// widest face (1060×888). A preview never animates, so the tide is in and the total shows its end value, except in the
// mid-tide preview, which holds the tide still. Games read Neutral here.

private val Zip = GameId("zip")
private val Tango = GameId("tango")
private val Queens = GameId("queens")
private val Sudoku = GameId("sudoku")
private val Strands = GameId("strands")

private val Names = mapOf(Zip to "Zip", Tango to "Tango", Queens to "Queens", Sudoku to "Mini Sudoku", Strands to "Strands")

private fun name(game: GameId): String = Names.getValue(game)

private val Month = RecapCard.Solved(
    total = 42,
    perGame = listOf(
        RecapCard.Solved.PerGame(Zip, 18),
        RecapCard.Solved.PerGame(Queens, 15),
        RecapCard.Solved.PerGame(Sudoku, 9),
    ),
    hintFree = 36,
    longestHintFreeRun = 12,
)

private val NoHintFree = Month.copy(hintFree = 0, longestHintFreeRun = null)

private val OneSolve = RecapCard.Solved(total = 1, perGame = listOf(RecapCard.Solved.PerGame(Zip, 1)))

/** A year of 1068 solves, Strands' 6 the thinnest band. */
private val Year = RecapCard.Solved(
    total = 1068,
    perGame = listOf(
        RecapCard.Solved.PerGame(Zip, 288),
        RecapCard.Solved.PerGame(Tango, 297),
        RecapCard.Solved.PerGame(Queens, 335),
        RecapCard.Solved.PerGame(Sudoku, 142),
        RecapCard.Solved.PerGame(Strands, 6),
    ),
    hintFree = 1061,
    longestHintFreeRun = 584,
)

/** Five digits under the longest period line. */
private val Longest = RecapCard.Solved(
    total = 12345,
    perGame = listOf(
        RecapCard.Solved.PerGame(Zip, 3402),
        RecapCard.Solved.PerGame(Tango, 2987),
        RecapCard.Solved.PerGame(Queens, 4120),
        RecapCard.Solved.PerGame(Sudoku, 1825),
        RecapCard.Solved.PerGame(Strands, 11),
    ),
    hintFree = 11980,
    longestHintFreeRun = 2140,
)

private const val LongestPeriod = "From 14 March 2023 to 21 November 2026"

/** The tide's linear progress in the mid-tide preview: the column a little over half full, the wave still moving. */
private const val MidTide = 0.45f

@Composable
private fun SolvedPreviewPage(card: RecapCard.Solved, period: String = "November 2026") = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { SolvedCardPage(card, period, ::name) }
}

@PuzzleBoxPreviews
@Composable
private fun SolvedCardPagePreview() = SolvedPreviewPage(Month)

@PuzzleBoxPreviews
@Composable
private fun SolvedCardPageNoHintFreePreview() = SolvedPreviewPage(NoHintFree)

@PuzzleBoxPreviews
@Composable
private fun SolvedCardPageOneSolvePreview() = SolvedPreviewPage(OneSolve)

@PuzzleBoxPreviews
@Composable
private fun SolvedCardPageLongestPreview() = SolvedPreviewPage(Longest, LongestPeriod)

@Preview(name = "Tablet Light", widthDp = 820, heightDp = 672, showBackground = true)
@Preview(name = "Tablet Dark", widthDp = 820, heightDp = 672, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SolvedCardPageTabletPreview() = SolvedPreviewPage(Month)

@Preview(name = "Tablet no hint-free", widthDp = 820, heightDp = 672, showBackground = true)
@Composable
private fun SolvedCardPageTabletNoHintFreePreview() = SolvedPreviewPage(NoHintFree)

@Preview(name = "Tablet one solve", widthDp = 820, heightDp = 672, showBackground = true)
@Composable
private fun SolvedCardPageTabletOneSolvePreview() = SolvedPreviewPage(OneSolve)

@Preview(name = "Wide Light", widthDp = 1060, heightDp = 888, showBackground = true)
@Preview(name = "Wide Dark", widthDp = 1060, heightDp = 888, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SolvedCardPageWidePreview() = SolvedPreviewPage(Year, "2025")

@Preview(name = "Wide longest", widthDp = 1060, heightDp = 888, showBackground = true)
@Composable
private fun SolvedCardPageWideLongestPreview() = SolvedPreviewPage(Longest, LongestPeriod)

/** The bands held mid-tide, so the wave and the half-filled column show without running the arrival. */
@PuzzleBoxPreviews
@Composable
private fun SolvedBandsMidTidePreview() = PuzzleBoxTheme {
    val tide = remember { SolvedTide(Year.total, MidTide) }
    Box(Modifier.size(width = 380.dp, height = 514.dp).background(PaletteEntry.Solved.field)) {
        SolvedBands(Year.perGame, ::name, BandMinWithStripe, tide, Modifier.fillMaxSize())
    }
}

@PuzzleBoxPreviews
@Composable
private fun SolvedCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { SolvedCardFact(Month, ::name) }
    }
}

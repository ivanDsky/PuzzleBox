package com.ivandsky.puzzlebox.insights.share

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.YearMonth

// The share image for a month and for a year still under way (A22), light and dark, in each style, with five games,
// two and one, drawn at a quarter of its real size.

private val Queens = GameId("queens")
private val Tango = GameId("tango")
private val Zip = GameId("zip")
private val Sudoku = GameId("sudoku")
private val Strands = GameId("strands")

private val Names = mapOf(Queens to "Queens", Tango to "Tango", Zip to "Zip", Sudoku to "Mini Sudoku", Strands to "Strands")

private fun name(game: GameId): String = Names.getValue(game)

private fun cards(vararg games: Pair<GameId, Int>): List<RecapCard> {
    val total = games.sumOf { it.second }
    return listOf(
        RecapCard.Solved(total = total, perGame = games.map { RecapCard.Solved.PerGame(it.first, it.second) }, hintFree = total * 6 / 7),
        RecapCard.TotalTime(totalMs = 11_535_000, solved = total, days = emptyList(), period = RecapPeriod.Month(YearMonth.of(2026, 11))),
        RecapCard.Streak(
            days = 17, from = LocalDate.of(2026, 11, 3), to = LocalDate.of(2026, 11, 19), freezesSpent = 2,
            frozenDays = emptySet(), span = RecapPeriod.Month(YearMonth.of(2026, 11)),
        ),
        RecapCard.Moments(List(14) { EarnedMoment(EarnedMoment.Kind.FullSet, LocalDate.of(2026, 11, 1 + it)) }),
    )
}

private val Month = cards(Queens to 28, Tango to 24, Zip to 22, Sudoku to 14, Strands to 8)
private val Year = cards(Queens to 334, Tango to 297, Zip to 288, Sudoku to 142, Strands to 6)

@Composable
private fun Poster(title: String, displayName: String?, cards: List<RecapCard>, style: ShareStyle) =
    ScaledToFit(Modifier.width(270.dp).height(480.dp)) { RecapShareImage(RecapShareContent.of(title, displayName, cards, ::name), style) }

@PuzzleBoxPreviews
@Composable
private fun RecapShareImageMonthPreview() = PuzzleBoxTheme { Poster("November 2026", "Ada", Month, ShareStyle.Classic) }

@PuzzleBoxPreviews
@Composable
private fun RecapShareImageYearSoFarPreview() = PuzzleBoxTheme { Poster("2026 so far", null, Year, ShareStyle.Classic) }

@PuzzleBoxPreviews
@Composable
private fun RecapShareImageColorPreview() = PuzzleBoxTheme { Poster("November 2026", "Ada", Month, ShareStyle.Color) }

@PuzzleBoxPreviews
@Composable
private fun RecapShareImageNightPreview() = PuzzleBoxTheme { Poster("November 2026", "Ada", Month, ShareStyle.Night) }

@PuzzleBoxPreviews
@Composable
private fun RecapShareImageTwoGamesPreview() = PuzzleBoxTheme { Poster("November 2026", "Ada", cards(Queens to 40, Zip to 18), ShareStyle.Classic) }

@PuzzleBoxPreviews
@Composable
private fun RecapShareImageOneGamePreview() = PuzzleBoxTheme { Poster("November 2026", "Ada", cards(Queens to 1).take(1), ShareStyle.Classic) }

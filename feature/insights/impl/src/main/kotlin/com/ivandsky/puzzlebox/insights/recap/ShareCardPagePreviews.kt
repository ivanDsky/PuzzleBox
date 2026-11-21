package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth

// The Share page light and dark: ready to share, and off while the history is restoring (A23).

private val Queens = GameId("queens")
private val Zip = GameId("zip")
private val Names = mapOf(Queens to "Queens", Zip to "Zip")

private fun name(game: GameId): String = Names.getValue(game)

private val Cards = listOf(
    RecapCard.Solved(total = 42, perGame = listOf(RecapCard.Solved.PerGame(Queens, 28), RecapCard.Solved.PerGame(Zip, 14)), hintFree = 36),
    RecapCard.TotalTime(totalMs = 11_535_000, solved = 42, days = emptyList(), period = RecapPeriod.Month(YearMonth.of(2026, 11))),
)

private val Ready = RecapShareState("November 2026", "Ada", restoring = false, sharing = false, failed = false)

@PuzzleBoxPreviews
@Composable
private fun ShareCardPagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { ShareCardPage(Cards, ::name, Ready, onShare = { _, _ -> }) }
}

@PuzzleBoxPreviews
@Composable
private fun ShareCardPageRestoringPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { ShareCardPage(Cards, ::name, Ready.copy(restoring = true), onShare = { _, _ -> }) }
}

package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.share.RecapShareState

// The Share page light and dark: ready to share, and off while the history is restoring (A23).

private val Cards = listOf(
    RecapCard.Solved(total = 42, perGame = emptyList()),
    RecapCard.TotalTime(totalMs = 11_535_000, solved = 42),
    RecapCard.HintFree(hintFree = 36, solved = 42),
)

private val Ready = RecapShareState("November 2026", "Ada", restoring = false, sharing = false, failed = false)

@PuzzleBoxPreviews
@Composable
private fun ShareCardPagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { ShareCardPage(Cards, Ready, onShare = { _, _ -> }) }
}

@PuzzleBoxPreviews
@Composable
private fun ShareCardPageRestoringPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { ShareCardPage(Cards, Ready.copy(restoring = true), onShare = { _, _ -> }) }
}

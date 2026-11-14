package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.DayMorph
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular

/**
 * The tablet's placement hero card above the board: where the viewer stands on [board] ([standing], big), and the
 * [score] that put them there (null: nothing to say, as with no solve in the window). A place that changes with the
 * board crossfades in place, and so do the board's name and the score ([DayMorph]); reduced motion shortens the
 * place's fade and swaps the other two.
 */
@Composable
internal fun RanksHeroCard(board: String, standing: Standing, score: String?, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ranks.hero"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Your place", style = MaterialTheme.typography.labelLarge)
                Crossfade(
                    standing.label(),
                    animationSpec = tween(if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium),
                    label = "heroPlace",
                ) { label ->
                    Text(label, style = MaterialTheme.typography.displayMedium.tabular(), modifier = Modifier.testTag("ranks.hero.place"))
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DayMorph(board, alignment = Alignment.TopEnd) {
                    Text(it, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.End)
                }
                DayMorph(score ?: standing.hint(), alignment = Alignment.TopEnd) { shown ->
                    Text(
                        shown,
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        textAlign = TextAlign.End,
                        modifier = Modifier.testTag("ranks.hero.score"),
                    )
                }
            }
        }
    }
}

/** What to say where the score goes when there is none. */
private fun Standing.hint(): String = when (this) {
    Standing.Pending -> "Loading the board"
    Standing.Beyond -> "Outside the top 100"
    else -> "Not ranked here yet"
}

@PuzzleBoxPreviews
@Composable
private fun RanksHeroCardPreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            RanksHeroCard("Zip", Standing.Place(3), "1:15 · 1 hint")
            RanksHeroCard("Overall", Standing.Place(12), "1,240 pts")
            RanksHeroCard("Tango", Standing.None, null)
        }
    }
}

package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.DayMorph
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular

/**
 * The tablet's placement hero card above the board, one compact line: where the viewer stands on [board] ([standing],
 * big), the board's name and the [score] that put them there (null: nothing to say, as with no solve in the window).
 * It wears the board's accent: [container] fills it and [ink] colours its text, both read while drawing, so a card
 * between two boards blends their colours without recomposing. A place that changes with the board crossfades in
 * place, and so do the board's name and the score ([DayMorph]); reduced motion shortens the place's fade and swaps
 * the other two.
 */
@Composable
internal fun RanksHeroCard(
    board: String,
    standing: Standing,
    score: String?,
    container: () -> Color,
    ink: () -> Color,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val color = remember(ink) { ColorProducer { ink() } }
    Row(
        modifier
            .fillMaxWidth()
            .testTag("ranks.hero")
            .clip(MaterialTheme.shapes.large)
            .drawBehind { drawRect(container()) }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Crossfade(
            standing.label(),
            animationSpec = tween(if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium),
            label = "heroPlace",
        ) { label ->
            BasicText(
                label,
                Modifier.testTag("ranks.hero.place").semantics { contentDescription = "Your place: ${standing.describe()}" },
                style = MaterialTheme.typography.displaySmall.tabular().copy(fontWeight = FontWeight.Bold),
                color = color,
            )
        }
        Column(Modifier.weight(1f)) {
            DayMorph(board) {
                BasicText(it, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DayMorph(score ?: standing.hint()) { shown ->
                BasicText(shown, Modifier.testTag("ranks.hero.score"), style = MaterialTheme.typography.bodyLarge.tabular(), color = color)
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
        val zip = AccentSlot.Blue.colors()
        val overall = AccentSlot.Neutral.colors()
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            RanksHeroCard("Zip", Standing.Place(3), "1:15 · 1 hint", { zip.container }, { zip.onContainer })
            RanksHeroCard("Overall", Standing.Place(12), "1,240 pts", { overall.container }, { overall.onContainer })
            RanksHeroCard("Tango", Standing.None, null, { overall.container }, { overall.onContainer })
        }
    }
}

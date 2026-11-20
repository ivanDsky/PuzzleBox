package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/** The tablet's list fact for the finale: the year total, then the three bests. */
@Composable
internal fun YearFinaleCardFact(card: RecapCard.YearFinale, gameName: (GameId) -> String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = FactRowHeight), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = "Year finale",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = card.total.toString(),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        RecapFactRows(
            rows = listOf(
                RecapFactRow("recap.finale.fact.total", "Year total", "${card.total} ${card.total.puzzles()}"),
                RecapFactRow("recap.finale.fact.best", "Best month", "${card.bestMonth.monthName()} · ${card.bestMonthSolved}"),
                RecapFactRow("recap.finale.fact.fastest", "Fastest solve", "${formatDuration(card.fastestTimeMs)} · ${gameName(card.fastestGame)}, ${card.fastestMonth.shortName(upper = false)}"),
                RecapFactRow("recap.finale.fact.most", "Most played", "${gameName(card.mostPlayedGame)} · ${card.mostPlayedSolved}"),
            ),
            tag = "recap.finale.facts",
        )
    }
}

private val FactRowHeight = 48.dp

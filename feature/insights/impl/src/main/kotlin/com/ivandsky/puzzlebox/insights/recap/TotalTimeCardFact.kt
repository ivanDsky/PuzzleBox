package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

/**
 * The Total time fact in the tablet list: the title with the time, a small river that follows the story's (a finger on
 * either reads the same bar on both) with its readout, then the puzzles and the average.
 */
@Composable
internal fun TotalTimeCardFact(card: RecapCard.TotalTime, modifier: Modifier = Modifier) {
    val bars = remember(card) { card.riverBars() }
    val scrub = rememberRiverScrub()
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = RowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = "Time spent solving",
                style = MaterialTheme.typography.titleMedium,
                color = ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatHeroDuration(card.totalMs),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(Spacing.md)
                .testTag("recap.totaltime.mini"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TotalTimeRiver(bars, scrub.value, ink, Modifier.fillMaxWidth().height(MiniHeight), gap = 1.dp, onScrub = { scrub.value = it })
            Readout(card.readout(bars, scrub.value), "recap.totaltime.miniReadout", Modifier.fillMaxWidth())
        }
        TotalTimeFactRows(card)
    }
}

private val RowMinHeight = 48.dp
private val MiniHeight = 64.dp

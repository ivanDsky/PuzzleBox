package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MaxTextWidth
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

/**
 * The Total time page: the time spent solving rolling up on arrival in the app's one time format, then the puzzles it
 * went on and the average per puzzle. A year of play runs to hours, so the hero is the time format at the display
 * size rather than the hero size the counts use.
 */
@Composable
internal fun TotalTimeCardPage(
    card: RecapCard.TotalTime,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    val shown = rememberCountUp(card.totalMs)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .testTag("recap.totaltime"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatDuration(shown),
            // The time rolls, so a screen reader reads the settled time, not each step of the roll.
            modifier = Modifier.semantics { contentDescription = "Time spent solving, ${formatDuration(card.totalMs)}" }.testTag("recap.totaltime.time"),
            style = MaterialTheme.typography.displayLarge.tabular(),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "spent solving",
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        TotalTimeFactRows(card, Modifier.widthIn(max = MaxTextWidth))
    }
}

/** The puzzles the time went on and the average per puzzle as one rounded block. Shared by the story and the tablet list. */
@Composable
internal fun TotalTimeFactRows(card: RecapCard.TotalTime, modifier: Modifier = Modifier) {
    RecapFactRows(
        rows = listOf(
            RecapFactRow("recap.totaltime.solved", "Puzzles solved", card.solved.toString()),
            RecapFactRow("recap.totaltime.average", "Average per puzzle", formatDuration(card.totalMs / card.solved)),
        ),
        tag = "recap.totaltime.facts",
        modifier = modifier,
    )
}

/** The Total time fact in the tablet list: the title with the time, then the puzzles and the average. */
@Composable
internal fun TotalTimeCardFact(card: RecapCard.TotalTime, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = RowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Time spent solving",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDuration(card.totalMs),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TotalTimeFactRows(card)
    }
}

private val RowMinHeight = 48.dp

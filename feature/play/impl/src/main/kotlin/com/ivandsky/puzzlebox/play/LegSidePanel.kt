package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.resultui.FlatProgressBar
import kotlin.time.Duration

// The figure-and-unit row of the progress and hints cards, as tall as the reference draws it.
private val StatRowHeight = 34.dp

/**
 * What a self-duel leg adds to its play screen: past you's [target] (null with the time-left setting off, so the
 * timer stays a plain count-up) and where the leg sits: [played] of [count] puzzles posted.
 */
internal data class SelfLeg(val target: Duration?, val played: Int, val count: Int)

/**
 * The tablet side panel of a self-duel leg, under the game's own caption slot ("Duel vs Past you · puzzle 2 of 3"): the duel's
 * progress and the hints used ([hints] is read live). The top bar holds the timer.
 */
internal fun legSidePanel(
    leg: SelfLeg,
    hints: () -> Int,
): @Composable ColumnScope.() -> Unit = {
    DuelProgressCard(leg.played, leg.count)
    HintsCard(hints())
}

@Composable
internal fun DuelProgressCard(played: Int, count: Int) {
    StatsCard(Modifier.testTag("leg.progress")) {
        CardLabel("Duel progress")
        Row(Modifier.height(StatRowHeight), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            BigNumber(played.toString(), Modifier.alignByBaseline(), MaterialTheme.typography.headlineMedium)
            SmallUnit("/$count played", Modifier.alignByBaseline())
        }
        FlatProgressBar(
            progress = { if (count == 0) 0f else played.toFloat() / count },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
    }
}

@Composable
internal fun HintsCard(used: Int) {
    StatsCard(Modifier.testTag("leg.hints")) {
        CardLabel("Hints")
        Row(Modifier.height(StatRowHeight), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            BigNumber(used.toString(), Modifier.alignByBaseline(), MaterialTheme.typography.headlineMedium)
            SmallUnit("used", Modifier.alignByBaseline())
        }
        Text(
            "Each hint counts against you in this duel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** A stat's figure: bold and tabular so the digits never jitter as they count. */
@Composable
private fun BigNumber(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.displayLarge) {
    Text(
        text,
        style = style.tabular(),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun SmallUnit(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

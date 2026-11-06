package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

/** The widest the stacked result actions grow: equal-width buttons, never a full tablet pane. */
private val ActionsMaxWidth = 360.dp

/**
 * The honest-best badge (tag `result.record_badge`, A10): "New best", "Tied your best!" or
 * "First solve"; nothing for a slower solve, a replay or an unknown verdict. A tonal tag,
 * not a button — chip height and radius, a leading trophy and the tertiary (amber) pair,
 * so it never borrows the lime fill or the 48 dp height of the actions below it.
 */
@Composable
internal fun RecordTag(verdict: RecordVerdict?, modifier: Modifier = Modifier) {
    val text = when (verdict) {
        RecordVerdict.NewBest -> "New best"
        RecordVerdict.TiedBest -> "Tied your best!"
        RecordVerdict.FirstSolve -> "First solve"
        RecordVerdict.NotBest, RecordVerdict.Replay, null -> return
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.testTag("result.record_badge"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_result_record),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The speed player's payoff (tag `result.previous_best`): a new best shows the score it
 * beat, a slower solve the standing best to chase, a replay says it was one. A first
 * solve has no previous best, and a tie's hero time IS the best: no line. A hints-first
 * game leads its best with the hints (A26) — "Best 0 hints · 1:42" — so the line ranks
 * the way the badge beside it does; time-first shows the time alone.
 */
@Composable
internal fun BestLine(verdict: RecordVerdict?, previousBest: BestScore?, rankOrder: RankOrder) {
    val best = previousBest?.let {
        when (rankOrder) {
            RankOrder.TimeThenHints -> formatDuration(it.timeMs)
            RankOrder.HintsThenTime -> "${hintCount(it.hints)} · ${formatDuration(it.timeMs)}"
        }
    }
    val line = when (verdict) {
        RecordVerdict.NewBest -> best?.let { "Previous best $it" }
        RecordVerdict.NotBest -> best?.let { "Best $it" }
        RecordVerdict.Replay -> best?.let { "Replay · Best $it" } ?: "Replay"
        RecordVerdict.TiedBest, RecordVerdict.FirstSolve, null -> null
    } ?: return
    Text(
        text = line,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("result.previous_best"),
    )
}

/** "1 hint" / "N hints", as the result's hints line reads. */
internal fun hintCount(hints: Int): String = if (hints == 1) "1 hint" else "$hints hints"

/**
 * The primary action (tagged `result.next` or `result.done`) over "Play again", stacked
 * at one equal width capped at [ActionsMaxWidth].
 */
@Composable
internal fun ResultActions(
    primary: ResultPrimaryAction,
    onPrimary: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = ActionsMaxWidth)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val fill = Modifier.fillMaxWidth()
        when (primary) {
            is ResultPrimaryAction.Next -> PrimaryButton(
                text = "Next: ${primary.name}",
                onClick = onPrimary,
                modifier = fill.testTag("result.next"),
            )
            ResultPrimaryAction.AllDone, ResultPrimaryAction.Done -> PrimaryButton(
                text = "Done",
                onClick = onPrimary,
                modifier = fill.testTag("result.done"),
            )
        }
        onPlayAgain?.let {
            SecondaryButton(
                text = "Play again",
                onClick = it,
                modifier = fill.testTag("result.playAgain"),
            )
        }
    }
}

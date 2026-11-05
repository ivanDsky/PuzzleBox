package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The solved-session result surface (ticket 23, spec UIUX-01 A3/A9). Its own nav route
 * paints an opaque scene — the finished board never bleeds through. Content is the
 * existing A3 facts only: time (the hero), hints, the honest-best badge, and "Play
 * again" (A9: no streaks, no percentiles, no new metrics).
 *
 * The badge renders from the in-memory [verdict] whenever it lands — this screen NEVER
 * waits on the record write (F2 §7 fire-and-observe, unchanged); [verdict] null means
 * the write is still in flight or the verdict is unknown. A10: "New best" only on a
 * strictly faster time (with the "Previous best" it beat), "Tied your best!" on the same
 * shown time, "First solve" on the game's first completion; a slower solve shows no
 * badge and "Best m:ss"; a replay shows no badge and "Replay · Best m:ss" (replays never
 * rank). A failed write stays surfaced and retryable here, where the player actually is
 * after solving.
 *
 * Core | game split: this shell is the same for every game. A game tweaks it through
 * [label] (its own line under the headline — game name + puzzle number), which
 * defaults to nothing.
 *
 * Restraint (senior-UX Lens 7): one accent moment — the best badge; type hierarchy
 * from AppTypography; theme roles only, zero ad-hoc colors.
 */
@Composable
fun ResultScreen(
    timeMs: Long,
    hintsUsed: Int,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    verdict: RecordVerdict? = null,
    previousBestMs: Long? = null,
    writeFailed: Boolean = false,
    onRetryWrite: () -> Unit = {},
    label: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("result.screen")
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        // §9: the completion announcement lives HERE (design §6: the board never
        // celebrates; the route handoff replaces it) — a polite liveRegion carrying the
        // exact "Puzzle solved" copy, announced when the result surface appears.
        Text(
            text = "Solved!",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Puzzle solved"
            },
        )
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("result.label"),
            )
        }
        // The hero metric (senior-UX Lens 4): time first, at display weight.
        Text(
            text = formatClock(timeMs),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // The run's ONE accent moment: the best badge, a tonal pill on the theme's
        // primary pair — no confetti, no second accent.
        val badge = when (verdict) {
            RecordVerdict.NewBest -> "New best"
            RecordVerdict.TiedBest -> "Tied your best!"
            RecordVerdict.FirstSolve -> "First solve"
            RecordVerdict.NotBest, RecordVerdict.Replay, null -> null
        }
        if (badge != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("result.record_badge"),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
        }
        // The speed player's payoff: a new best shows the time it beat; a slower solve
        // the standing best to chase; a replay says it was one. A first solve has no
        // previous best, and a tie's hero time IS the best.
        val best = previousBestMs?.let(::formatClock)
        val bestLine = when (verdict) {
            RecordVerdict.NewBest -> best?.let { "Previous best $it" }
            RecordVerdict.NotBest -> best?.let { "Best $it" }
            RecordVerdict.Replay -> best?.let { "Replay · Best $it" } ?: "Replay"
            RecordVerdict.TiedBest, RecordVerdict.FirstSolve, null -> null
        }
        if (bestLine != null) {
            Text(
                text = bestLine,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("result.previous_best"),
            )
        }
        Text(
            text = if (hintsUsed == 1) "1 hint" else "$hintsUsed hints",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (writeFailed) {
            // Standard error convention for the record write — surfaced where the
            // player is, retryable, never blocking the result (F2 §7).
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("result.write_failed"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Couldn't save your record.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                AppTextButton(text = "Retry", onClick = onRetryWrite)
            }
        }
        PrimaryButton(
            text = "Play again",
            onClick = onPlayAgain,
            modifier = Modifier.testTag("result.play_again"),
        )
    }
}

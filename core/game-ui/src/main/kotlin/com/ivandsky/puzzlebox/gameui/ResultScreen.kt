package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.GameId

/**
 * The solved-session result surface (ticket 23, spec UIUX-01 A3/A9). Its own nav route
 * paints an opaque scene — the finished board never bleeds through. Content is the
 * existing A3 facts only: time (the hero), hints and the new-record badge (A9: no
 * streaks, no percentiles, no new metrics).
 *
 * Actions (A7, SC10): [primary] is the one filled button, handed back through
 * [onPrimary] — "Next: <game>", "All done for today" with Done, or a replay's Done. While
 * [primary] is null (the next step still unknown) the action row keeps its place but is
 * invisible and inert, so the centred content never jumps when it lands.
 * "Play again" ([onPlayAgain]) is the quiet secondary; null hides it.
 *
 * The badge renders from the in-memory verdict whenever it lands — this screen NEVER
 * waits on the record write (F2 §7 fire-and-observe, unchanged); [isRecord] null means
 * the write is still in flight or the verdict is unknown. A failed write stays
 * surfaced and retryable here, where the player actually is after solving.
 *
 * Core | game split: this shell is the same for every game. A game tweaks it through
 * [label] (its own line under the headline — game name + puzzle number), which
 * defaults to nothing.
 *
 * Restraint (senior-UX Lens 7): one accent moment — the record badge; type hierarchy
 * from AppTypography; theme roles only, zero ad-hoc colors.
 */
@Composable
fun ResultScreen(
    timeMs: Long,
    hintsUsed: Int,
    primary: ResultPrimaryAction?,
    onPrimary: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isRecord: Boolean? = null,
    writeFailed: Boolean = false,
    onRetryWrite: () -> Unit = {},
    label: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Short windows (landscape phone, split screen) scroll instead of squeezing the
            // actions to nothing; fillMaxSize first keeps short content centred.
            .verticalScroll(rememberScrollState())
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
        // The run's ONE accent moment: the record badge, a tonal pill on the theme's
        // primary pair — no confetti, no second accent.
        if (isRecord == true) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("result.record_badge"),
            ) {
                Text(
                    text = "New personal record",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
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
                TextButton(onClick = onRetryWrite) { Text("Retry") }
            }
        }
        val pending = primary == null
        Column(
            modifier = if (pending) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ResultActions(
                primary = primary ?: ResultPrimaryAction.Done,
                onPrimary = { if (!pending) onPrimary() },
                onPlayAgain = onPlayAgain?.let { again -> { if (!pending) again() } },
            )
        }
    }
}

/** The primary action (tagged `result.next` or `result.done`) over "Play again". */
@Composable
private fun ResultActions(
    primary: ResultPrimaryAction,
    onPrimary: () -> Unit,
    onPlayAgain: (() -> Unit)?,
) {
    if (primary == ResultPrimaryAction.AllDone) {
        Text(
            text = "All done for today",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("result.all_done"),
        )
    }
    when (primary) {
        is ResultPrimaryAction.Next -> PrimaryButton(
            text = "Next: ${primary.name}",
            onClick = onPrimary,
            modifier = Modifier.testTag("result.next"),
        )
        ResultPrimaryAction.AllDone, ResultPrimaryAction.Done -> PrimaryButton(
            text = "Done",
            onClick = onPrimary,
            modifier = Modifier.testTag("result.done"),
        )
    }
    onPlayAgain?.let {
        SecondaryButton(
            text = "Play again",
            onClick = it,
            modifier = Modifier.testTag("result.playAgain"),
        )
    }
}

// Previews — internal, light + dark inside PuzzleBoxTheme: today's live result (badge,
// label, Next), a cold result after process death (no badge), all done, and a replay.

@PuzzleBoxPreviews
@Composable
internal fun ResultScreenLivePreview() {
    PuzzleBoxTheme {
        ResultScreen(
            timeMs = 95_000,
            hintsUsed = 1,
            primary = ResultPrimaryAction.Next(GameId("tango"), "Tango"),
            onPrimary = {},
            onPlayAgain = {},
            isRecord = true,
            label = "Zip · #42",
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultScreenColdPreview() {
    PuzzleBoxTheme {
        ResultScreen(
            timeMs = 95_000,
            hintsUsed = 0,
            primary = ResultPrimaryAction.Next(GameId("tango"), "Tango"),
            onPrimary = {},
            onPlayAgain = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultScreenAllDonePreview() {
    PuzzleBoxTheme {
        ResultScreen(
            timeMs = 95_000,
            hintsUsed = 2,
            primary = ResultPrimaryAction.AllDone,
            onPrimary = {},
            onPlayAgain = {},
            label = "Zip · #42",
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultScreenReplayPreview() {
    PuzzleBoxTheme {
        ResultScreen(
            timeMs = 95_000,
            hintsUsed = 0,
            primary = ResultPrimaryAction.Done,
            onPrimary = {},
            onPlayAgain = {},
            label = "Zip · #41",
        )
    }
}

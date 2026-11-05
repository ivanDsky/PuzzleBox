package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.model.GameId

/**
 * Below this pane height (a landscape phone, split screen, a 470 dp window under the
 * shell's bars) the result top-aligns and tightens so both actions stay in view.
 */
private val CompactHeight = 480.dp

/**
 * The solved-session result surface (ticket 23, spec UIUX-01 A3/A9). Its own nav route
 * paints an opaque scene — the finished board never bleeds through. Content is the
 * existing A3 facts only: time (the hero), hints and the honest-best badge with its
 * previous-best line (A9, A10: no streaks, no percentiles, no new metrics).
 *
 * Actions (A7, SC10): [primary] is the one filled button, handed back through
 * [onPrimary] — "Next: <game>", Done under the "All done for today" sub-headline, or a
 * replay's Done. While [primary] is null (the next step still unknown) the action column
 * keeps its place but is invisible and inert, so the buttons never jump when they land;
 * the all-done sub-headline is added only once [primary] says so.
 * "Play again" ([onPlayAgain]) is the quiet secondary, stacked at the primary's width;
 * null hides it.
 *
 * Height (s11): a pane under [CompactHeight] top-aligns with a smaller time and tighter
 * gaps so both actions stay visible; anything taller centres. Either way the column
 * scrolls rather than squeezing the actions to nothing.
 *
 * The badge renders from the in-memory [verdict] whenever it lands — this screen NEVER
 * waits on the record write (F2 §7 fire-and-observe, unchanged); null means the write is
 * still in flight or the verdict is unknown. [previousBestMs] is the best before this play. A failed write stays
 * surfaced and retryable here, where the player actually is after solving.
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
    primary: ResultPrimaryAction?,
    onPrimary: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    modifier: Modifier = Modifier,
    verdict: RecordVerdict? = null,
    previousBestMs: Long? = null,
    writeFailed: Boolean = false,
    onRetryWrite: () -> Unit = {},
    label: String? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < CompactHeight
        val gap = if (compact) Spacing.sm else Spacing.md
        Column(
            modifier = Modifier
                .fillMaxSize()
                // fillMaxSize first: short content still fills the pane, so a tall pane
                // centres it while a short one scrolls instead of clipping the actions.
                .verticalScroll(rememberScrollState())
                .testTag("result.screen")
                .padding(
                    if (compact) {
                        PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm)
                    } else {
                        PaddingValues(Spacing.xl)
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                gap,
                if (compact) Alignment.Top else Alignment.CenterVertically,
            ),
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
            // s05: the day's closure is the sub-headline, not a line buried above Done.
            if (primary == ResultPrimaryAction.AllDone) {
                Text(
                    text = "All done for today",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("result.all_done"),
                )
            }
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
                text = formatDuration(timeMs),
                style = if (compact) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.displayMedium
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
            // The run's ONE accent moment: the best badge — no confetti, no second accent.
            RecordTag(verdict)
            BestLine(verdict, previousBestMs)
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
            ResultActions(
                primary = primary ?: ResultPrimaryAction.Done,
                onPrimary = { if (!pending) onPrimary() },
                onPlayAgain = onPlayAgain?.let { again -> { if (!pending) again() } },
                // A tall pane sets the actions apart; a compact one spends no extra height.
                modifier = Modifier.padding(top = if (compact) 0.dp else gap).then(
                    if (pending) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
                ),
            )
        }
    }
}

// Previews — internal, light + dark inside PuzzleBoxTheme: today's live result (new best,
// label, Next), a cold result after process death (no verdict), all done, and a replay.

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
            verdict = RecordVerdict.NewBest,
            previousBestMs = 112_000,
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
            verdict = RecordVerdict.Replay,
            previousBestMs = 88_000,
            label = "Zip · #41",
        )
    }
}

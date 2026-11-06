package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.Instant

/**
 * Below this pane height (a landscape phone, split screen, a 470 dp window under the
 * shell's bars) the result top-aligns and tightens so both actions stay in view.
 */
private val CompactHeight = 480.dp

/** The widest the result card grows: one readable column, never a full tablet pane. */
private val CardMaxWidth = 420.dp

/**
 * The solved-session result (spec A16): its own nav route paints one opaque card in
 * place of the board. In order: the header — [brand]'s glyph (stamped on entry), the
 * puzzle ([game] and its [puzzleNumber]) and the record pill; the time as the hero
 * number; the comparison to your best ([previousBest], ranked by [rankOrder]); the Hints · Streak · Today
 * tiles; the primary step; then Share ([onShare]) and "Play again" ([onPlayAgain], null
 * hides it).
 *
 * A [practice] puzzle (no edition number) never ranks: no record pill and no Streak tile,
 * whatever [verdict] or [streak] say. A null [streak], [doneToday] or [total] hides its
 * tile until it is known.
 *
 * [primary] (A7, SC10) is handed back through [onPrimary]: "Next: <game>", or a replay's
 * Done; [ResultPrimaryAction.AllDone] shows the countdown to [rolloverAt] instead of a
 * button. While [primary] is null (the next step still unknown) the actions keep their
 * place but are invisible and inert, so nothing jumps when they land.
 *
 * Height (s11): a pane under [CompactHeight] top-aligns with tighter gaps; anything taller
 * centres. Either way the column scrolls rather than squeezing the actions to nothing.
 *
 * The pill renders from the in-memory [verdict] whenever it lands — this screen NEVER
 * waits on the record write (F2 §7 fire-and-observe); null means the write is still in
 * flight or the verdict is unknown. A failed write ([writeFailed]) stays surfaced and
 * retryable through [onRetryWrite], where the player is after solving.
 */
@Composable
fun ResultScreen(
    timeMs: Long,
    hintsUsed: Int,
    primary: ResultPrimaryAction?,
    onPrimary: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    modifier: Modifier = Modifier,
    brand: GameBrand = GameBrand.Neutral,
    game: String = "",
    puzzleNumber: Int? = null,
    practice: Boolean = false,
    verdict: RecordVerdict? = null,
    previousBest: BestScore? = null,
    rankOrder: RankOrder = RankOrder.TimeThenHints,
    streak: Int? = null,
    doneToday: Int? = null,
    total: Int? = null,
    rolloverAt: Instant? = null,
    writeFailed: Boolean = false,
    onRetryWrite: () -> Unit = {},
) {
    val shownVerdict = verdict.takeUnless { practice }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < CompactHeight
        val gap = if (compact) Spacing.sm else Spacing.lg
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
                        PaddingValues(Spacing.lg)
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (compact) Arrangement.Top else Arrangement.Center,
        ) {
            OutlinedCard(
                modifier = Modifier.widthIn(max = CardMaxWidth).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(if (compact) Spacing.md else Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    ResultHeader(brand, resultTitle(game, puzzleNumber, practice), shownVerdict)
                    Column {
                        // The hero metric: time first, at Display, tabular.
                        Text(
                            text = formatDuration(timeMs),
                            style = MaterialTheme.typography.displayLarge.tabular(),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("result.time"),
                        )
                        BestLine(shownVerdict, previousBest, rankOrder, timeMs, brand)
                    }
                    ResultStatTiles(
                        hintsUsed = hintsUsed,
                        streak = streak.takeUnless { practice },
                        doneToday = doneToday,
                        total = total,
                    )
                    if (writeFailed) WriteFailedRow(onRetryWrite)
                    val pending = primary == null
                    ResultActions(
                        primary = primary ?: ResultPrimaryAction.Done,
                        onPrimary = { if (!pending) onPrimary() },
                        onShare = { if (!pending) onShare() },
                        onPlayAgain = onPlayAgain?.let { again -> { if (!pending) again() } },
                        rolloverAt = rolloverAt,
                        modifier = if (pending) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
                    )
                }
            }
        }
    }
}

/** The record write's standard error row — surfaced where the player is, retryable, never blocking (F2 §7). */
@Composable
private fun WriteFailedRow(onRetryWrite: () -> Unit) {
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
        TextButton(onClick = onRetryWrite, modifier = Modifier.testTag("result.retry")) { Text("Retry") }
    }
}

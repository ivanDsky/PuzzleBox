package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import kotlin.math.roundToInt

/**
 * Below this pane height (a landscape phone, split screen, a 470 dp window under the
 * shell's bars) the result top-aligns and tightens so both actions stay in view.
 */
private val CompactHeight = 480.dp

/** The widest the result card grows: one readable column, never a full tablet pane. */
private val CardMaxWidth = 420.dp

/**
 * A friend puzzle's result (spec A16): one opaque card in place of the board. In order:
 * the header — [brand]'s glyph (stamped on entry), the puzzle's name ([game]) and the
 * record pill; the time as the hero number; a play-again's line naming the best it left
 * standing ([previousBest], ranked by [rankOrder]); the Hints tile; [details]; then
 * the Done step ([onDone]), Share ([onShare]) and "Play again" ([onPlayAgain], null hides it).
 *
 * The pill renders from the in-memory [verdict] whenever it lands, so this screen never
 * waits on a record write; null means the verdict is unknown. [details] is the result's own
 * block under the tile (a friend's puzzle names its author and their time there). [below]
 * draws under the card, in the same column and width, so it never pushes the actions.
 *
 * Height (s11): a pane under [CompactHeight] top-aligns with tighter gaps; anything taller
 * centres. Either way the column scrolls rather than squeezing the actions to nothing.
 */
@Composable
fun ResultScreen(
    timeMs: Long,
    hintsUsed: Int,
    onDone: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    modifier: Modifier = Modifier,
    brand: GameBrand = GameBrand.Neutral,
    game: String = "",
    verdict: RecordVerdict? = null,
    previousBest: BestScore? = null,
    rankOrder: RankOrder = RankOrder.TimeThenHints,
    details: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < CompactHeight
        val gap = if (compact) Spacing.sm else Spacing.lg
        Box(
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
            propagateMinConstraints = true,
        ) {
            CardWithTail(centred = !compact, gap = gap, tail = below) {
                OutlinedCard(
                    modifier = Modifier.widthIn(max = CardMaxWidth).fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(if (compact) Spacing.md else Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        ResultHeader(brand, game, verdict)
                        Column {
                            // The hero metric: time first, at Display, tabular.
                            Text(
                                text = formatDuration(timeMs),
                                style = MaterialTheme.typography.displayLarge.tabular(),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("result.time"),
                            )
                            if (verdict == RecordVerdict.PlayAgain) PlayAgainLine(previousBest, rankOrder)
                        }
                        ResultStatTiles(hintsUsed)
                        details?.invoke()
                        ResultActions(
                            primary = ResultPrimaryAction.Done,
                            onPrimary = onDone,
                            onShare = onShare,
                            onPlayAgain = onPlayAgain,
                            rolloverAt = null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The card, centred in the pane when [centred] (else at the top), and [tail] one [gap] under it in the same
 * [CardMaxWidth] column. The tail never takes part in the centring, so it can come and go without moving the
 * card; the pane scrolls when card and tail together outgrow it.
 */
@Composable
private fun CardWithTail(centred: Boolean, gap: Dp, tail: (@Composable () -> Unit)?, card: @Composable () -> Unit) {
    Layout(
        content = {
            Box(contentAlignment = Alignment.TopCenter) { card() }
            if (tail != null) Box(Modifier.widthIn(max = CardMaxWidth).fillMaxWidth()) { tail() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val cardPlaceable = measurables[0].measure(loose)
        val tailPlaceable = measurables.getOrNull(1)?.measure(loose)
        val paneHeight = constraints.minHeight
        val cardTop = if (centred) ((paneHeight - cardPlaceable.height) / 2f).roundToInt().coerceAtLeast(0) else 0
        val tailTop = cardTop + cardPlaceable.height + gap.roundToPx()
        val height = maxOf(paneHeight, tailPlaceable?.let { tailTop + it.height } ?: 0)
        layout(constraints.maxWidth, height) {
            cardPlaceable.place(((constraints.maxWidth - cardPlaceable.width) / 2f).roundToInt(), cardTop)
            tailPlaceable?.place(((constraints.maxWidth - tailPlaceable.width) / 2f).roundToInt(), tailTop)
        }
    }
}

/** The record write's standard error row — surfaced where the player is, retryable, never blocking (F2 §7). */
@Composable
internal fun WriteFailedRow(onRetryWrite: () -> Unit) {
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

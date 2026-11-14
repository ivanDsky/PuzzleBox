package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.ivandsky.puzzlebox.designsystem.MorphCell
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryReplays
import com.ivandsky.puzzlebox.history.ReplaySolve
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** The viewer's own solve of the board's day: the [row] (its record is set), that day's [replays] and the game's [playUi]. */
internal data class YourSolve(
    val row: HistoryDayRow,
    val replays: HistoryReplays,
    val playUi: GamePlayUi?,
)

/**
 * A compact row under the calendar, one line: the time and hints of [solve], then two icon buttons, the day's replays
 * (Watch replay; with two different solves, a menu of both and the compare, see [SolveReplayButton]) and Play again.
 * It is local data, so it shows signed out and offline. Null shows nothing.
 *
 * The card is one card whatever solve it shows. When the solve changes (another game, day or window, a refresh) only
 * what differs moves, in place: the numbers of the time and hints roll ([RollingLine]), and the game's glyph, the
 * words and the buttons frost over ([MorphCell]). A solve that appears or goes frosts in or out as a whole while the
 * row's height eases to it, so what is under the row never jumps. Reduced motion swaps.
 */
@Composable
internal fun YourSolveRow(
    solve: YourSolve?,
    onPlayAgain: (GameId, LocalDate) -> Unit,
    onOpenReplay: (HistoryDayRow, ReplaySolve) -> Unit,
    onCompare: (HistoryDayRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resize = if (rememberReducedMotion()) {
        Modifier
    } else {
        Modifier.animateContentSize(spring(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault))
    }
    MorphCell(solve, modifier.fillMaxWidth().then(resize), identity = { it != null }) { shown ->
        if (shown != null) SolveCard(shown, onPlayAgain, onOpenReplay, onCompare)
    }
}

@Composable
private fun SolveCard(
    solve: YourSolve,
    onPlayAgain: (GameId, LocalDate) -> Unit,
    onOpenReplay: (HistoryDayRow, ReplaySolve) -> Unit,
    onCompare: (HistoryDayRow) -> Unit,
) {
    val record = solve.row.record ?: return
    val ink = MaterialTheme.colorScheme.onSurface
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md).testTag("ranks.yoursolve"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.padding(start = Spacing.md, top = Spacing.sm, end = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SolvePart(solve, { it.row.brand }) { GameGlyph(brand = it.row.brand, size = GameGlyphSize.Chip) }
            Column(Modifier.weight(1f).padding(start = Spacing.xs)) {
                Text("Your solve", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RollingLine(
                    "${formatDuration(record.time)} · ${hintsText(record.hints)}",
                    MaterialTheme.typography.titleMedium.tabular(),
                    remember(ink) { ColorProducer { ink } },
                    Modifier.testTag("ranks.yoursolve.score"),
                )
                SolvePart(solve, { it.shownReplays().unavailableText() }) { shown ->
                    shown.shownReplays().unavailableText()?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Two solves whose button looks the same keep it still: only its taps go to the new solve.
            SolvePart(solve, { it.shownReplays()::class }) { shown ->
                SolveReplayButton(shown.shownReplays(), onOpen = { onOpenReplay(shown.row, it) }, onCompare = { onCompare(shown.row) })
            }
            SolvePart(solve, { it.row.brand.accent }) { shown ->
                val accent = shown.row.brand.accent.colors()
                FilledTonalIconButton(
                    onClick = { onPlayAgain(shown.row.gameId, shown.row.day) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = accent.container, contentColor = accent.onContainer),
                    modifier = Modifier.size(SolveButtonSize).testTag("ranks.yoursolve.playagain"),
                ) { Icon(PuzzleIcons.Retry, contentDescription = "Play again") }
            }
        }
    }
}

/**
 * A part of the card that frosts over when its [look] changes and is otherwise still, drawn from the solve of this
 * frame. The layer on its way out says nothing to assistive tech or a test, so no tag is on screen twice.
 */
@Composable
private fun <L> SolvePart(solve: YourSolve, look: (YourSolve) -> L, content: @Composable (YourSolve) -> Unit) {
    MorphCell(solve, identity = look) { layer ->
        val current = look(layer) == look(solve)
        Box(if (current) Modifier else Modifier.clearAndSetSemantics { }) { content(if (current) solve else layer) }
    }
}

// The one place that reads replayBoard here: a game with no replay board shows no way into a replay.
private fun YourSolve.shownReplays(): HistoryReplays = replays.takeIf { playUi?.replayBoard != null } ?: HistoryReplays.Hidden

private fun hintsText(hints: Int) = when (hints) {
    0 -> "no hints"
    1 -> "1 hint"
    else -> "$hints hints"
}

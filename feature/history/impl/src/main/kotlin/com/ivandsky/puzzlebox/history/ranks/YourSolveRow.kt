package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryReplaySection
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

private val ButtonHeight = 48.dp

/**
 * A compact row under the calendar: the time and hints of [solve], Play again, and the day's replays (Watch replay;
 * with two different solves, both and the compare). It is local data, so it shows signed out and offline.
 * It eases in and out with the day (a fade and a size change; none under reduced motion); null shows nothing.
 */
@Composable
internal fun YourSolveRow(
    solve: YourSolve?,
    onPlayAgain: (GameId, LocalDate) -> Unit,
    onOpenReplay: (HistoryDayRow, ReplaySolve) -> Unit,
    onCompare: (HistoryDayRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    AnimatedContent(
        targetState = solve,
        modifier = modifier,
        transitionSpec = {
            if (reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard)) togetherWith
                    fadeOut(tween(MotionTokens.DurationExit)) using
                    SizeTransform(clip = true) { _, _ -> tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard) }
            }
        },
        contentKey = { it?.row?.key },
        label = "yourSolve",
    ) { shown ->
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
    val row = solve.row
    val record = row.record ?: return
    val accent = row.brand.accent.colors()
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md).testTag("ranks.yoursolve"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                GameGlyph(brand = row.brand, size = GameGlyphSize.Chip)
                Column(Modifier.weight(1f)) {
                    Text("Your solve", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${formatDuration(record.time)} · ${hintsText(record.hints)}",
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("ranks.yoursolve.score"),
                    )
                }
                FilledTonalButton(
                    onClick = { onPlayAgain(row.gameId, row.day) },
                    shape = PillShape,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.container, contentColor = accent.onContainer),
                    modifier = Modifier.heightIn(min = ButtonHeight).testTag("ranks.yoursolve.playagain"),
                ) { Text("Play again", style = MaterialTheme.typography.labelLarge) }
            }
            HistoryReplaySection(
                replays = solve.replays,
                ui = solve.playUi,
                onOpen = { onOpenReplay(row, it) },
                onCompare = { onCompare(row) },
            )
        }
    }
}

private fun hintsText(hints: Int) = when (hints) {
    0 -> "no hints"
    1 -> "1 hint"
    else -> "$hints hints"
}

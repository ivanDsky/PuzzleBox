package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import java.util.Locale

/**
 * The play tab's fresh-entry state (spec A14): today's set, one row per registered game
 * in registry order with its status — "Not started", "In progress · m:ss" (a kept
 * session, ADR-0010) or "Solved · m:ss ✓"; no subtitle until the status is known.
 * Each known status also names what a tap does — Play, Continue or Replay — in the
 * primary-text colour, so the row reads as a control and a kept session is offered
 * as a continue. Picking a row starts or resumes that game in place; no new
 * navigation pattern. The picker never fetches (the play's load does).
 */
@Composable
internal fun GamePicker(
    games: List<PlayUiState.Picker.Row>,
    onPick: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag("play.picker")
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Pick a game",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        games.forEach { entry ->
            val action = entry.status?.let(::actionLabel)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("play.picker_${entry.game.value}")
                    // §9 touch floors: picker rows clear 48 dp with the padded content.
                    .heightIn(min = 56.dp)
                    .clickable(onClickLabel = action, onClick = { onPick(entry.game) }),
            ) {
                Row(
                    Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = entry.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        entry.status?.let { status ->
                            Text(
                                text = statusLine(status),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("play.picker_status_${entry.game.value}"),
                            )
                        }
                    }
                    action?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalPrimaryText.current,
                            modifier = Modifier
                                .padding(start = Spacing.md)
                                .testTag("play.picker_action_${entry.game.value}"),
                        )
                    }
                }
            }
        }
    }
}

/** A row's status copy: the clock reads m:ss (0:42, 12:05). */
internal fun statusLine(status: TodayStatus): String = when (status) {
    TodayStatus.NotStarted -> "Not started"
    is TodayStatus.InProgress -> "In progress · ${minutesSeconds(status.elapsedMs)}"
    is TodayStatus.Solved -> "Solved · ${minutesSeconds(status.timeMs)} ✓"
}

/** What tapping a row does: a kept session continues, a solved day replays. */
internal fun actionLabel(status: TodayStatus): String = when (status) {
    TodayStatus.NotStarted -> "Play"
    is TodayStatus.InProgress -> "Continue"
    is TodayStatus.Solved -> "Replay"
}

private fun minutesSeconds(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

@PuzzleBoxPreviews
@Composable
private fun GamePickerPreview() {
    PuzzleBoxTheme {
        GamePicker(
            games = listOf(
                PlayUiState.Picker.Row(GameId("zip"), "Zip", TodayStatus.Solved(36_000L)),
                PlayUiState.Picker.Row(GameId("tango"), "Tango", TodayStatus.InProgress(42_000L)),
                PlayUiState.Picker.Row(GameId("queens"), "Queens", TodayStatus.NotStarted),
                PlayUiState.Picker.Row(GameId("pinpoint"), "Pinpoint", status = null),
            ),
            onPick = {},
        )
    }
}

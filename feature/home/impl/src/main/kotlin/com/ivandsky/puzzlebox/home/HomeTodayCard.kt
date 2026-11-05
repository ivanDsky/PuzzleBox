package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.TodayStatus
import java.util.Locale

/** The primary action's label: "Resume" when a game of today's set has a save (A13, SC11). */
internal fun playTodayLabel(resume: Boolean): String = if (resume) "Resume" else "Play today"

/**
 * The one filled card (A15): today's set — every game's status, the done count, and the
 * primary action (tag `home.play_today`), which [resume] relabels.
 */
@Composable
internal fun TodayCard(
    today: List<HomeUiState.TodayRow>,
    resume: Boolean,
    onPlayToday: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home.today_card"),
        shape = MaterialTheme.shapes.large,
        color = cardColor,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TodaySet(today = today, tagPrefix = "home.today")
            PrimaryButton(
                text = playTodayLabel(resume),
                onClick = onPlayToday,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md)
                    .testTag("home.play_today"),
            )
        }
    }
}

/**
 * The "n of N done" summary over one row per game, shared by the Today card and the
 * two-pane detail. [tagPrefix] keeps the two copies apart in tests.
 */
@Composable
internal fun TodaySet(
    today: List<HomeUiState.TodayRow>,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = doneSummary(today),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(bottom = Spacing.xs)
                .testTag("${tagPrefix}_summary"),
        )
        today.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${tagPrefix}_row_${row.game.value}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = row.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                row.status?.let { status ->
                    Text(
                        text = statusLine(status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("${tagPrefix}_status_${row.game.value}"),
                    )
                }
            }
        }
    }
}

/** "1 of 3 done": a game is done once today's puzzle is solved. */
internal fun doneSummary(today: List<HomeUiState.TodayRow>): String =
    "${today.count { it.status is TodayStatus.Solved }} of ${today.size} done"

/** The play picker's status copy (A14), so both surfaces read the same: the clock is m:ss. */
internal fun statusLine(status: TodayStatus): String = when (status) {
    TodayStatus.NotStarted -> "Not started"
    is TodayStatus.InProgress -> "In progress · ${minutesSeconds(status.elapsedMs)}"
    is TodayStatus.Solved -> "Solved · ${minutesSeconds(status.timeMs)} ✓"
}

private fun minutesSeconds(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

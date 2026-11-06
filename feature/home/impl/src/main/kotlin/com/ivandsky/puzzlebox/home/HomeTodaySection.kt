package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import kotlinx.coroutines.delay

private const val TICK_MS = 1_000L

// Matches PrimaryButton's 52 dp so the CTA and the countdown swap without a jump.
private val CtaHeight = 52.dp

/**
 * Today's set (A9, A10) as the [HomeTodaySummary] card, tagged for tests from [tagPrefix]:
 * `<prefix>_summary`, `<prefix>_play`, `<prefix>_countdown`; badges read `home.badge.<id>`
 * for the `home.today` copy.
 */
@Composable
internal fun HomeTodaySection(
    state: HomeUiState,
    onOpenGame: (GameId) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    HomeTodaySummary(
        state = state,
        onOpenGame = onOpenGame,
        tagPrefix = tagPrefix,
        badgeTagPrefix = "${tagPrefix.removeSuffix("today")}badge.",
        modifier = modifier,
    )
}

/**
 * "New puzzles in 3:12:08": a quiet tonal pill in the CTA's place, ticking once a second
 * only while the screen is at least STARTED, and resting at 0:00 once [rolloverAt] passes.
 */
@Composable
internal fun RolloverCountdown(rolloverAt: Instant, modifier: Modifier = Modifier) {
    var remainingMs by remember(rolloverAt) { mutableLongStateOf(msUntil(rolloverAt)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(rolloverAt, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            remainingMs = msUntil(rolloverAt)
            while (remainingMs > 0) {
                delay(TICK_MS)
                remainingMs = msUntil(rolloverAt)
            }
        }
    }
    Surface(
        modifier = modifier.heightIn(min = CtaHeight),
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "New puzzles in ${formatDuration(remainingMs)}",
                style = MaterialTheme.typography.titleMedium.tabular(),
            )
        }
    }
}

private fun msUntil(at: Instant): Long = (at.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)

/** "Continue Zip" for a saved play, "Play Tango" otherwise. */
internal fun ctaLabel(target: HomeUiState.TodayRow): String =
    if (target.status is TodayStatus.InProgress) "Continue ${target.displayName}" else "Play ${target.displayName}"

/** "1 of 3 done": a game is done once today's puzzle is solved. */
internal fun doneSummary(today: List<HomeUiState.TodayRow>): String =
    "${today.count { it.status is TodayStatus.Solved }} of ${today.size} done"

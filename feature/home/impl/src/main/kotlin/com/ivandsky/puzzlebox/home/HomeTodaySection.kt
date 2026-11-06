package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import kotlinx.coroutines.delay

// From 4 games the tiles wrap into rows of 3 (3 + 2 for five, identity §2.8).
private const val GRID_FROM = 4
private const val GRID_COLUMNS = 3
private const val TICK_MS = 1_000L

// Matches PrimaryButton's 52 dp so the CTA and the countdown swap without a jump.
private val CtaHeight = 52.dp

/**
 * Today's set (A10): one [TodayTile] per game, then the one primary action naming
 * [HomeUiState.target] — "Continue" when it has a save, "Play" otherwise — which becomes
 * the countdown to [HomeUiState.rolloverAt] once every game is solved. Tapping a tile or
 * the CTA is [onOpenGame]. [tagPrefix] keeps the list and detail copies apart in tests:
 * `<prefix>_tile_<id>`, `<prefix>_play`, `<prefix>_countdown`.
 */
@Composable
internal fun HomeTodaySection(
    state: HomeUiState,
    onOpenGame: (GameId) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(tagPrefix),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = doneSummary(state.today),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("${tagPrefix}_summary"),
        )
        TileGrid(state.today, onOpenGame, tagPrefix)
        val target = state.target
        when {
            state.allSolved -> RolloverCountdown(
                rolloverAt = state.rolloverAt,
                modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_countdown"),
            )
            target != null -> PrimaryButton(
                text = ctaLabel(target),
                onClick = { onOpenGame(target.game) },
                modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_play"),
            )
        }
    }
}

@Composable
private fun TileGrid(
    today: List<HomeUiState.TodayRow>,
    onOpenGame: (GameId) -> Unit,
    tagPrefix: String,
) {
    if (today.isEmpty()) return
    val columns = if (today.size >= GRID_FROM) GRID_COLUMNS else today.size
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        today.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                row.forEach { entry ->
                    TodayTile(
                        entry = entry,
                        onClick = { onOpenGame(entry.game) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("${tagPrefix}_tile_${entry.game.value}"),
                    )
                }
                // A short last row keeps its tiles the grid's width.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * "New puzzles in 3:12:08": a quiet tonal pill in the CTA's place, ticking once a second
 * only while the screen is at least STARTED, and resting at 0:00 once [rolloverAt] passes.
 */
@Composable
private fun RolloverCountdown(rolloverAt: Instant, modifier: Modifier = Modifier) {
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
private fun ctaLabel(target: HomeUiState.TodayRow): String =
    if (target.status is TodayStatus.InProgress) "Continue ${target.displayName}" else "Play ${target.displayName}"

/** "1 of 3 done": a game is done once today's puzzle is solved. */
private fun doneSummary(today: List<HomeUiState.TodayRow>): String =
    "${today.count { it.status is TodayStatus.Solved }} of ${today.size} done"

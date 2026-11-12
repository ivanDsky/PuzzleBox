package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
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
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import kotlinx.coroutines.delay

private const val TICK_MS = 1_000L

/** The countdown keeps the primary's height, so the actions under it sit where they would. */
private val CountdownMinHeight = 52.dp

/**
 * The one primary step [ResultScreen] offers (spec A7, A16, SC10). Today's result offers
 * [Next] (the next unsolved daily, named) or, when every daily is solved, [AllDone]: the
 * countdown to the next puzzles stands in for the button. A play-again's result offers [Done].
 */
sealed interface ResultPrimaryAction {

    /** "Next: [name]": open [gameId]'s today. */
    data class Next(val gameId: GameId, val name: String) : ResultPrimaryAction

    /** Every daily of today is solved: "All done for today" over the countdown, no button. */
    data object AllDone : ResultPrimaryAction

    /** A play-again's result: Done returns to the origin. */
    data object Done : ResultPrimaryAction
}

/**
 * [ResultPrimaryAction.AllDone]'s place (tag `result.all_done`): "All done for today"
 * over the time left until [rolloverAt] (tag `result.countdown`, tabular, ticking each
 * second on the wall clock, never below 0:00). A null [rolloverAt] (not read yet) shows
 * the closing line alone.
 */
@Composable
internal fun ResultCountdown(rolloverAt: Instant?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.heightIn(min = CountdownMinHeight).testTag("result.all_done"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
    ) {
        Text(
            text = "All done for today",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (rolloverAt != null) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(rolloverAt) {
                while (now < rolloverAt.toEpochMilli()) {
                    delay(TICK_MS - now % TICK_MS)
                    now = System.currentTimeMillis()
                }
            }
            Text(
                text = "New puzzles in ${formatDuration(rolloverAt.toEpochMilli() - now)}",
                style = MaterialTheme.typography.bodyLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("result.countdown"),
            )
        }
    }
}

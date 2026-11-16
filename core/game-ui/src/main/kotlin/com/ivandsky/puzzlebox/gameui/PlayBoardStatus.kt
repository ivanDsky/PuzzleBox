package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing

internal const val TODAYS_PUZZLE = "Getting today's puzzle…"
internal const val THE_PUZZLE = "Getting the puzzle…"

/** The play screen's async board states (A7/A8) — degrade states are first-class. */
sealed interface PlayBoardStatus {
    /** [todaysDaily]: the slot is the game's today, the only one whose loading copy says "today's puzzle". */
    data class Loading(val todaysDaily: Boolean = false) : PlayBoardStatus

    /** Nothing playable; [message] drives the error copy. Retry re-runs the fetch, offered
     *  only when [retryable] — a day the source says doesn't exist won't appear on retry. */
    data class Unavailable(val message: String, val retryable: Boolean = true) : PlayBoardStatus

    /** Board in hand and playable. */
    data object Ready : PlayBoardStatus
}

/**
 * The board area while no board is drawn: the error for [PlayBoardStatus.Unavailable] (with
 * Retry when it is retryable), the loading indicator otherwise. Test tags are
 * `"$tagPrefix.error"` / `"$tagPrefix.loading"`.
 */
@Composable
fun PlayBoardFallback(status: PlayBoardStatus, onRetry: () -> Unit, tagPrefix: String) {
    when (status) {
        is PlayBoardStatus.Unavailable -> BoardError(
            message = status.message,
            onRetry = onRetry.takeIf { status.retryable },
            // A short board area (landscape phone, split screen) scrolls so Retry stays reachable.
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(Spacing.xl)
                .testTag("$tagPrefix.error"),
        )

        else -> BoardLoading(
            label = if ((status as? PlayBoardStatus.Loading)?.todaysDaily == true) TODAYS_PUZZLE else THE_PUZZLE,
            modifier = Modifier.testTag("$tagPrefix.loading"),
        )
    }
}

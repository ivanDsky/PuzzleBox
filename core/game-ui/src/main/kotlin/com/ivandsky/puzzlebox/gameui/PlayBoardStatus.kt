package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The play screen's async board states (A7/A8) — degrade states are first-class. */
sealed interface PlayBoardStatus {
    data object Loading : PlayBoardStatus

    /** Nothing playable; [message] drives the error copy. Retry re-runs the fetch, offered
     *  only when [retryable] — a day the source says doesn't exist won't appear on retry. */
    data class Unavailable(val message: String, val retryable: Boolean = true) : PlayBoardStatus

    /** Board in hand. [banner] non-null = playing the SAVED puzzle because the fetch
     *  failed — surfaced as a banner, the board stays playable. */
    data class Ready(val banner: String?) : PlayBoardStatus
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

        else -> BoardLoading(modifier = Modifier.testTag("$tagPrefix.loading"))
    }
}

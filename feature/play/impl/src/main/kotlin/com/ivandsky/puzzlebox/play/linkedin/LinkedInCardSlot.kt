package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.play.LibraryRowModel
import com.ivandsky.puzzlebox.play.LibraryViewModel
import com.ivandsky.puzzlebox.play.libraryCardColors
import kotlinx.coroutines.flow.StateFlow

/**
 * What the game page hands its day card for LinkedIn: the [view] to collect and the three actions.
 * A null [onReconnect] means there is no login to open.
 */
internal class LinkedInCardState(
    val view: StateFlow<LinkedInDayView?>,
    val onResume: () -> Unit,
    val onRetry: () -> Unit,
    val onReconnect: (() -> Unit)?,
)

/**
 * The day card's LinkedIn lines for [row]. The view is collected here, not higher up, so only a
 * composed day card reads or syncs anything: the games list never does, and a card that leaves
 * the screen cancels its sync. A null view draws nothing.
 */
@Composable
internal fun LinkedInCardSlot(card: LinkedInCardState, row: LibraryRowModel) {
    val view by card.view.collectAsStateWithLifecycle()
    val shown = view ?: return
    // Read once per view so the "Updated" age does not tick while the card stays open.
    val nowMs = remember(shown) { System.currentTimeMillis() }
    LinkedInDayLines(
        view = shown,
        order = row.card.rankOrder,
        colors = libraryCardColors(row.card.brand),
        nowMs = nowMs,
        onResume = card.onResume,
        onReconnect = card.onReconnect,
        onRetry = card.onRetry,
    )
}

/** The page's card state, or null while the feature is off (the view model has no holder then). */
@Composable
internal fun rememberLinkedInCard(viewModel: LibraryViewModel, onReconnect: (() -> Unit)?): LinkedInCardState? {
    val holder = viewModel.linkedIn ?: return null
    return remember(viewModel, onReconnect) {
        LinkedInCardState(viewModel.linkedInView, holder::resume, holder::retry, onReconnect)
    }
}

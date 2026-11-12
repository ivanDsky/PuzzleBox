package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.play.LibraryViewModel
import kotlinx.coroutines.delay
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
 * The day card's LinkedIn sync chip. The view is collected here, not higher up, so only a composed day card reads or
 * syncs anything: the games list never does, and a card that leaves the screen cancels its sync.
 *
 * A day switch must not flip the chip to "Syncing" and back, so the chip keeps drawing the last settled status through a
 * read; only past [RING_DELAY_MS] of reading does its mark become a ring, and a settled change morphs. The chip is
 * not unmounted when the view goes null between days. With nothing settled yet it draws logo and ring at once; with
 * no view and nothing settled it draws nothing.
 */
@Composable
internal fun LinkedInCardSlot(card: LinkedInCardState) {
    val view by card.view.collectAsStateWithLifecycle()
    val status = view?.status
    val shown = rememberLastSettled(status) ?: if (status == LinkedInDayStatus.Loading) LinkedInDayStatus.Loading else return
    val reading = status == LinkedInDayStatus.Loading
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(reading) {
        slow = false
        if (reading) {
            delay(RING_DELAY_MS)
            slow = true
        }
    }
    LinkedInSyncChip(
        status = shown,
        reading = reading && slow,
        onResume = card.onResume,
        onReconnect = card.onReconnect,
        onRetry = card.onRetry,
    )
}

/** How long a read runs before the chip's mark becomes a ring: a quick read never shows one. */
private const val RING_DELAY_MS = 1_000L

/** The last [status] that was not Loading (and not absent), kept through the reads and gaps between days. */
@Composable
private fun rememberLastSettled(status: LinkedInDayStatus?): LinkedInDayStatus? {
    val last = remember { arrayOfNulls<LinkedInDayStatus>(1) }
    if (status != null && status != LinkedInDayStatus.Loading) last[0] = status
    return last[0]
}

/** The page's card state, or null while the feature is off (the view model has no holder then). */
@Composable
internal fun rememberLinkedInCard(viewModel: LibraryViewModel, onReconnect: (() -> Unit)?): LinkedInCardState? {
    val holder = viewModel.linkedIn ?: return null
    return remember(viewModel, onReconnect) {
        LinkedInCardState(viewModel.linkedInView, holder::resume, holder::retry, onReconnect)
    }
}

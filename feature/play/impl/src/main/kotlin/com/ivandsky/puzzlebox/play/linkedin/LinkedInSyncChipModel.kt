package com.ivandsky.puzzlebox.play.linkedin

import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind

/**
 * What the sync chip shows. A null [label] is the synced state, which draws a check instead of words; a null [onClick]
 * is a chip with nothing to offer. [description] is the whole state for a screen reader, since the label alone ("Retry")
 * does not say what it retries.
 */
internal class SyncChipModel(val label: String?, val description: String, val onClick: (() -> Unit)?)

/**
 * The chip for [status], or null when there is nothing to say (LinkedIn is not connected). A paused sync offers Resume
 * alone, whatever stopped it but an ended session: Retry would hit the very latch that paused it. A null [onReconnect]
 * means there is no login to open, so an ended session is reported and not offered.
 */
internal fun syncChipOf(
    status: LinkedInDayStatus,
    onResume: () -> Unit,
    onReconnect: (() -> Unit)?,
    onRetry: () -> Unit,
): SyncChipModel? {
    val signIn = if (onReconnect != null) SyncChipModel("Sign in again", "LinkedIn session ended. Sign in again", onReconnect)
    else SyncChipModel("Session ended", "LinkedIn session ended", null)
    return when (status) {
        LinkedInDayStatus.NotConnected -> null
        LinkedInDayStatus.Loading -> SyncChipModel("Syncing…", "Syncing with LinkedIn", null)
        is LinkedInDayStatus.Imported, LinkedInDayStatus.DidNotPlay, LinkedInDayStatus.Idle ->
            SyncChipModel(null, "Synced with LinkedIn", null)
        LinkedInDayStatus.Offline -> SyncChipModel("Retry", "Can't reach LinkedIn. Retry", onRetry)
        LinkedInDayStatus.SessionExpired -> signIn
        is LinkedInDayStatus.Paused ->
            if (status.kind == Kind.SessionExpired) signIn else SyncChipModel("Resume", "LinkedIn sync paused. Resume", onResume)
    }
}

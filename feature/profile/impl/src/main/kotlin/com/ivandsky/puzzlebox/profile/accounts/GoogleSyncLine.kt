package com.ivandsky.puzzlebox.profile.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.app.screens.ProfileUiState.GoogleSync
import com.ivandsky.puzzlebox.data.cloud.PauseReason
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

private const val SYNCING = "Syncing…"
private const val OFFLINE = "No connection, so your history didn't sync. It will when you're online."
private const val DELETING = "Deleting…"
private const val PAUSED_EXHAUSTED = "Sync paused · over today's limit, tries again later"
private const val PAUSED_WRITE_CAP = "Sync paused · this phone is over its daily limit, tries again later"
private const val PAUSED_DENIED = "Sync paused · your account was refused, tries again later"

/**
 * The signed-in Google row's sync line: what the engine is doing, with Sync now beside it (idle while a pass is
 * already running or a delete is) and, under it, how many drafts are too big to sync (no line at 0) and why the last
 * Delete my cloud data did not finish. [nowMs] only phrases the age.
 */
@Composable
internal fun GoogleSyncLine(
    sync: GoogleSync,
    nowMs: Long,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("profile.google.sync"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = if (sync.erasing) DELETING else syncText(sync.status, nowMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).testTag("profile.google.sync.status"),
            )
            AppTextButton(
                text = "Sync now",
                onClick = onSyncNow,
                modifier = Modifier.testTag("profile.google.sync.now"),
                enabled = sync.status != GoogleSync.Status.Syncing && !sync.erasing,
            )
        }
        if (sync.oversizedDrafts > 0) {
            Text(
                text = oversizedDraftsText(sync.oversizedDrafts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("profile.google.sync.drafts"),
            )
        }
        sync.eraseMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("profile.google.sync.erase-message"),
            )
        }
    }
}

private fun syncText(status: GoogleSync.Status, nowMs: Long): String = when (status) {
    is GoogleSync.Status.Synced -> "Synced · ${relativeAgeText(status.atMs, nowMs)}"
    GoogleSync.Status.Syncing -> SYNCING
    GoogleSync.Status.Offline -> OFFLINE
    is GoogleSync.Status.Paused -> pausedText(status.reason)
}

private fun oversizedDraftsText(count: Int): String =
    if (count == 1) "1 draft is too big to sync" else "$count drafts are too big to sync"

private fun pausedText(reason: PauseReason): String = when (reason) {
    PauseReason.Exhausted -> PAUSED_EXHAUSTED
    PauseReason.WriteCap -> PAUSED_WRITE_CAP
    PauseReason.Denied -> PAUSED_DENIED
}

private const val PREVIEW_NOW_MS = 1_800_000_000_000L
private const val PREVIEW_MINUTE_MS = 60_000L

@Composable
private fun SyncLinePreview(
    status: GoogleSync.Status,
    eraseMessage: String? = null,
    erasing: Boolean = false,
    oversizedDrafts: Int = 0,
) {
    PuzzleBoxTheme {
        GoogleSyncLine(
            GoogleSync(status, erasing = erasing, eraseMessage = eraseMessage, oversizedDrafts = oversizedDrafts),
            PREVIEW_NOW_MS,
            onSyncNow = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun GoogleSyncLineSyncedPreview() =
    SyncLinePreview(GoogleSync.Status.Synced(PREVIEW_NOW_MS - 2 * PREVIEW_MINUTE_MS))

@PuzzleBoxPreviews
@Composable
internal fun GoogleSyncLineSyncingPreview() = SyncLinePreview(GoogleSync.Status.Syncing)

@PuzzleBoxPreviews
@Composable
internal fun GoogleSyncLineOfflinePreview() = SyncLinePreview(GoogleSync.Status.Offline)

@PuzzleBoxPreviews
@Composable
internal fun GoogleSyncLinePausedExhaustedPreview() = SyncLinePreview(GoogleSync.Status.Paused(PauseReason.Exhausted))

@PuzzleBoxPreviews
@Composable
internal fun GoogleSyncLinePausedWriteCapPreview() = SyncLinePreview(GoogleSync.Status.Paused(PauseReason.WriteCap))

@PuzzleBoxPreviews
@Composable
internal fun GoogleSyncLinePausedDeniedPreview() = SyncLinePreview(GoogleSync.Status.Paused(PauseReason.Denied))

@PuzzleBoxPreviews
@Composable
internal fun GoogleSyncLineOneOversizedDraftPreview() =
    SyncLinePreview(GoogleSync.Status.Synced(PREVIEW_NOW_MS - 2 * PREVIEW_MINUTE_MS), oversizedDrafts = 1)

@PuzzleBoxPreviews
@Composable
internal fun GoogleSyncLineOversizedDraftsPreview() =
    SyncLinePreview(GoogleSync.Status.Synced(PREVIEW_NOW_MS - 2 * PREVIEW_MINUTE_MS), oversizedDrafts = 3)

@PuzzleBoxPreviews
@Composable
internal fun GoogleSyncLineDeletingPreview() =
    SyncLinePreview(GoogleSync.Status.Synced(PREVIEW_NOW_MS - 2 * PREVIEW_MINUTE_MS), erasing = true)

@PuzzleBoxPreviews
@Composable
internal fun GoogleSyncLineEraseOfflinePreview() = SyncLinePreview(
    GoogleSync.Status.Synced(PREVIEW_NOW_MS - 2 * PREVIEW_MINUTE_MS),
    eraseMessage = "You're offline. Try again when you're connected.",
)

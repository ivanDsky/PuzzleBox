package com.ivandsky.puzzlebox.profile.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.profile.R

private val LeadSize = 40.dp
private val MarkSize = 22.dp
private val MenuMinWidth = 180.dp
private val MenuItemHeight = 48.dp

private const val SIGN_IN_COPY = "Sign in with Google to put your times on the leaderboards."
private const val SIGN_IN_SYNC_COPY = "Sign in with Google to put your times on the leaderboards and keep your history on every device."
private const val SIGN_OUT_COPY = "Signing out keeps your history on this phone and in your account."
private const val UNFINISHED_COPY = "Unfinished games stay on this device."

/**
 * The Google account row: signed in it says so with Sign out beside it; signed out it explains the sign-in and
 * carries the button in its extra line, with a failure message right under it. With cloud sync on ([sync] given)
 * the signed-in row adds the sync line, what stays after Sign out and that unfinished games stay on this device (A35),
 * and an overflow holding Delete my cloud data behind its confirm; without it the row is exactly the plain one.
 * [syncCopy] (cloud sync is bound; [sync] is null while signed out) makes the signed-out explanation name keeping the
 * history on every device. [nowMs] only phrases the sync age.
 */
@Composable
internal fun GoogleRow(
    account: ProfileUiState.Account,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    sync: ProfileUiState.GoogleSync? = null,
    syncCopy: Boolean = sync != null,
    nowMs: Long = 0L,
    onSyncNow: () -> Unit = {},
    onDeleteCloudData: () -> Unit = {},
) {
    when (account) {
        is ProfileUiState.Account.SignedIn -> AccountRow(
            lead = { GoogleMark() },
            title = "Google",
            sub = "Signed in with Google",
            modifier = modifier.testTag("profile-signed-in"),
            trailing = {
                if (sync == null) {
                    AppTextButton(text = "Sign out", onClick = onSignOut)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        AppTextButton(text = "Sign out", onClick = onSignOut)
                        GoogleOverflow(enabled = !sync.erasing, onDeleteCloudData = onDeleteCloudData)
                    }
                }
            },
            extra = sync?.let { { GoogleSyncExtra(it, nowMs, onSyncNow) } },
        )
        is ProfileUiState.Account.SignedOut -> AccountRow(
            lead = { GoogleMark() },
            title = "Google",
            sub = if (syncCopy) SIGN_IN_SYNC_COPY else SIGN_IN_COPY,
            modifier = modifier.testTag("profile-signed-out"),
            extra = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PrimaryButton(
                        text = if (account.signingIn) "Signing in…" else "Sign in with Google",
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !account.signingIn,
                    )
                    account.signInMessage?.let { message ->
                        Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }
}

/** The signed-in row's extra with cloud sync on: the sync line, then what stays after Sign out and on this device. */
@Composable
private fun GoogleSyncExtra(sync: ProfileUiState.GoogleSync, nowMs: Long, onSyncNow: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        GoogleSyncLine(sync, nowMs, onSyncNow)
        Text(text = SIGN_OUT_COPY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = UNFINISHED_COPY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The signed-in Google row's options: Delete my cloud data, which asks first in [DeleteCloudDataDialog] and calls
 * [onDeleteCloudData] only on its Delete. Not offered while a delete is already running ([enabled] false).
 */
@Composable
private fun GoogleOverflow(enabled: Boolean, onDeleteCloudData: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    // Saved so a rotation does not close the confirm.
    var confirming by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("profile.google.overflow")) {
            Icon(
                imageVector = PuzzleIcons.More,
                contentDescription = "Google account options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Delete my cloud data",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                },
                onClick = {
                    open = false
                    confirming = true
                },
                enabled = enabled,
                modifier = Modifier.defaultMinSize(minWidth = MenuMinWidth, minHeight = MenuItemHeight).testTag("profile.google.delete"),
            )
        }
    }
    if (confirming) {
        DeleteCloudDataDialog(
            onConfirm = {
                confirming = false
                onDeleteCloudData()
            },
            onCancel = { confirming = false },
        )
    }
}

/** Google's own colours on a white disc: the brand mark does not follow the theme. */
@Composable
private fun GoogleMark() {
    Box(
        modifier = Modifier
            .size(LeadSize)
            .background(Color.White, CircleShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(MarkSize))
    }
}

private const val PREVIEW_NOW_MS = 1_800_000_000_000L
private val PreviewSignedIn = ProfileUiState.Account.SignedIn(Identity(uid = "u1", displayName = "Ann", photoUrl = null))

@PuzzleBoxPreviews
@Composable
internal fun GoogleRowSyncedPreview() {
    PuzzleBoxTheme {
        GoogleRow(
            account = PreviewSignedIn,
            onSignIn = {},
            onSignOut = {},
            sync = ProfileUiState.GoogleSync(ProfileUiState.GoogleSync.Status.Synced(PREVIEW_NOW_MS - 120_000L)),
            nowMs = PREVIEW_NOW_MS,
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun GoogleRowEraseOfflinePreview() {
    PuzzleBoxTheme {
        GoogleRow(
            account = PreviewSignedIn,
            onSignIn = {},
            onSignOut = {},
            sync = ProfileUiState.GoogleSync(
                ProfileUiState.GoogleSync.Status.Offline,
                eraseMessage = "You're offline. Try again when you're connected.",
            ),
            nowMs = PREVIEW_NOW_MS,
        )
    }
}

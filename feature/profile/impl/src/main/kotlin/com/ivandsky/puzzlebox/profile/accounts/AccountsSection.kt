package com.ivandsky.puzzlebox.profile.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * Profile's Accounts: Google first, then LinkedIn in one card. The LinkedIn row is drawn only when the feature is
 * on ([LinkedInActions.onConnect] given) and the link has reported a [linkedIn] state; the two accounts are
 * independent. The Google row carries cloud sync when [account] says so; the LinkedIn row carries the saved-boards
 * copy and Delete saved boards only when [boards] is given.
 */
@Composable
internal fun AccountsSection(
    account: ProfileUiState.Account,
    linkedIn: LinkedInRowState?,
    boards: ProfileUiState.LinkedInBoards?,
    nowMs: Long,
    google: GoogleActions,
    linkedInActions: LinkedInActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = "Accounts", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                GoogleRow(account, google.onSignIn, google.onSignOut, google.onSyncNow, google.onDeleteCloudData, nowMs = nowMs)
                val onConnect = linkedInActions.onConnect
                if (linkedIn != null && onConnect != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinkedInRow(
                        linkedIn,
                        nowMs,
                        onConnect,
                        linkedInActions.onResume,
                        linkedInActions.onDisconnect,
                        linkedInActions.onDeleteSavedBoards,
                        boards = boards,
                    )
                }
            }
        }
    }
}

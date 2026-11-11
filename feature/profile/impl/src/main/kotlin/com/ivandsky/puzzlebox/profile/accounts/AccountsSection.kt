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
 * on ([onConnectLinkedIn] given) and the link has reported a [linkedIn] state; the two accounts are independent.
 */
@Composable
internal fun AccountsSection(
    account: ProfileUiState.Account,
    linkedIn: LinkedInRowState?,
    nowMs: Long,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onConnectLinkedIn: (() -> Unit)?,
    onResumeLinkedIn: () -> Unit,
    onDisconnectLinkedIn: () -> Unit,
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
                GoogleRow(account, onSignIn, onSignOut)
                if (linkedIn != null && onConnectLinkedIn != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinkedInRow(linkedIn, nowMs, onConnectLinkedIn, onResumeLinkedIn, onDisconnectLinkedIn)
                }
            }
        }
    }
}

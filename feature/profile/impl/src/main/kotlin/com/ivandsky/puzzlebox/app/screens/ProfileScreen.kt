package com.ivandsky.puzzlebox.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.profile.ProfileAvatar
import com.ivandsky.puzzlebox.profile.ProfileHeader
import com.ivandsky.puzzlebox.profile.accounts.AccountsSection

/**
 * Profile (A20): the top bar (back icon at the left, [onOpenSettings] gear at the right),
 * then the header (avatar, name and "Playing since") and the Accounts card: the Google row, and the
 * LinkedIn row when [onConnectLinkedIn] is given (the feature is on). [nowMs] phrases the LinkedIn and sync update times.
 */
@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    nowMs: Long,
    modifier: Modifier = Modifier,
    onConnectLinkedIn: (() -> Unit)? = null,
    onResumeLinkedIn: () -> Unit = {},
    onDisconnectLinkedIn: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onDeleteCloudData: () -> Unit = {},
    onDeleteSavedBoards: () -> Unit = {},
) {
    val account = state.account
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ProfileTopBar(onBack = onBack, onOpenSettings = onOpenSettings)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Under the top bar the content starts where Settings' does.
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            // The blocks sit as far apart as Settings'.
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            val name = when (account) {
                is ProfileUiState.Account.SignedIn -> account.identity.displayName.ifEmpty { "Player" }
                is ProfileUiState.Account.SignedOut -> "Guest player"
            }
            ProfileHeader(
                name = name,
                playingSince = state.playingSince,
                avatar = {
                    ProfileAvatar(
                        name = name,
                        photoUrl = (account as? ProfileUiState.Account.SignedIn)?.identity?.photoUrl,
                        signedIn = account is ProfileUiState.Account.SignedIn,
                    )
                },
            )
            AccountsSection(
                account = account,
                linkedIn = state.linkedIn,
                nowMs = nowMs,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
                onConnectLinkedIn = onConnectLinkedIn,
                onResumeLinkedIn = onResumeLinkedIn,
                onDisconnectLinkedIn = onDisconnectLinkedIn,
                sync = state.sync,
                onSyncNow = onSyncNow,
                onDeleteCloudData = onDeleteCloudData,
                boards = state.linkedInBoards,
                onDeleteSavedBoards = onDeleteSavedBoards,
            )
        }
    }
}

/** Profile's bar: the back icon and the gear, each with its own callback. */
@Composable
private fun ProfileTopBar(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    CompactTopBar(
        title = "Profile",
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("profile.back")) {
                Icon(
                    imageVector = PuzzleIcons.Back,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("profile.settings")) {
                Icon(
                    imageVector = PuzzleIcons.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

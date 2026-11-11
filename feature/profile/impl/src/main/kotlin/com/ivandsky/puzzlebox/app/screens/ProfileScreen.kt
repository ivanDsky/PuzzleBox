package com.ivandsky.puzzlebox.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.profile.ProfileAvatar
import com.ivandsky.puzzlebox.profile.ProfileHeader

/**
 * Profile (A20): the top bar (back icon at the left, [onOpenSettings] gear at the right),
 * then the header (avatar, name and "Playing since") and the account: Google sign-in, or
 * the account line with Sign out.
 */
@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
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
            when (account) {
                is ProfileUiState.Account.SignedOut -> SignedOutSection(account, onSignIn)
                is ProfileUiState.Account.SignedIn -> SignedInSection(onSignOut)
            }
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

@Composable
private fun SignedOutSection(account: ProfileUiState.Account.SignedOut, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("profile-signed-out"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Sign in with Google to put your times on the leaderboards.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onSignIn, enabled = !account.signingIn) {
            Text(if (account.signingIn) "Signing in…" else "Sign in with Google")
        }
        account.signInMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SignedInSection(onSignOut: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("profile-signed-in"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Signed in with Google",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        AppTextButton(text = "Sign out", onClick = onSignOut)
    }
}

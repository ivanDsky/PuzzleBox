package com.ivandsky.puzzlebox.profile.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.profile.R

private val LeadSize = 40.dp
private val MarkSize = 22.dp

/**
 * The Google account row: signed in it says so with Sign out beside it; signed out it explains the sign-in and
 * carries the button in its extra line, with a failure message right under it.
 */
@Composable
internal fun GoogleRow(
    account: ProfileUiState.Account,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (account) {
        is ProfileUiState.Account.SignedIn -> AccountRow(
            lead = { GoogleMark() },
            title = "Google",
            sub = "Signed in with Google",
            modifier = modifier.testTag("profile-signed-in"),
            trailing = { AppTextButton(text = "Sign out", onClick = onSignOut) },
        )
        is ProfileUiState.Account.SignedOut -> AccountRow(
            lead = { GoogleMark() },
            title = "Google",
            sub = "Sign in with Google to put your times on the leaderboards.",
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

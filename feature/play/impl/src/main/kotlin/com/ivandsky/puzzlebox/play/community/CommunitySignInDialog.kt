package com.ivandsky.puzzlebox.play.community

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.play.board.BoardSignIn

/**
 * What a signed-out Report opens: reports are tied to an account, so it asks for Google ([onSignIn]) and says to tap
 * Report again afterwards. [signIn] is how the last try went; [onDismiss] is Not now.
 */
@Composable
internal fun CommunitySignInDialog(signIn: BoardSignIn, onSignIn: () -> Unit, onDismiss: () -> Unit) {
    val busy = signIn == BoardSignIn.SigningIn
    AppDialog(
        title = "Sign in to report",
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("community.signin"),
        content = {
            Text(
                "Sign in with Google, then tap Report again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (signIn) {
                BoardSignIn.Failed -> FailureLine("Couldn't sign in. Try again.", "community.signin.failed")
                BoardSignIn.Offline -> FailureLine("You're offline. Sign in once you're back.", "community.signin.offline")
                BoardSignIn.Idle, BoardSignIn.SigningIn -> Unit
            }
        },
        actions = {
            AppTextButton("Not now", onClick = onDismiss, modifier = Modifier.testTag("community.signin.dismiss"))
            SecondaryButton(
                text = if (busy) "Signing in…" else "Sign in with Google",
                onClick = onSignIn,
                enabled = !busy,
                modifier = Modifier.testTag("community.signin.google"),
            )
        },
    )
}

@Composable
private fun FailureLine(text: String, tag: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(tag))
}

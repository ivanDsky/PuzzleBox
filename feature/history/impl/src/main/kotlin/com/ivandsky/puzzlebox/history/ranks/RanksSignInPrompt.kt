package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The board's signed-out state, in place (A3): boards are read only when signed in, everything else on the screen is local. */
@Composable
internal fun RanksSignInPrompt(signIn: RanksSignIn, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag("ranks.signIn")
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Sign in to see the ranks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            "Your solves stay on this phone either way. Playing never needs it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        val busy = signIn == RanksSignIn.SigningIn
        SecondaryButton(
            text = if (busy) "Signing in…" else "Sign in with Google",
            onClick = onSignIn,
            enabled = !busy,
            modifier = Modifier.testTag("ranks.signIn.button"),
        )
        val failure = when (signIn) {
            RanksSignIn.Failed -> "ranks.signIn.failed" to "Couldn't sign in. Try again."
            RanksSignIn.Offline -> "ranks.signIn.offline" to "You're offline. Sign in once you're back."
            RanksSignIn.Idle, RanksSignIn.SigningIn -> null
        }
        failure?.let { (tag, text) ->
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(tag))
        }
    }
}

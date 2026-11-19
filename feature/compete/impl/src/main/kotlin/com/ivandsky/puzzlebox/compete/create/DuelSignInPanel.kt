package com.ivandsky.puzzlebox.compete.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SIGN_IN_WITH_GOOGLE
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The panel a signed-out player meets first for a friend duel: Sign in with Google, or Cancel to close New duel.
 * With Past you offered it keeps the "Play against" row, the way to a duel that needs no account.
 */
@Composable
internal fun SignInPanel(state: NewDuelState, actions: NewDuelActions) {
    val signIn = state.signIn
    val signingIn = signIn == DuelSignIn.SigningIn
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Sign in to duel friends", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "Duels tie each result to you, so friends can find you and the outcome is fair. Playing puzzles never needs an account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.selfOffered) PlayAgainstRow(state.opponent, state.selfName, actions.onOpponent, selfPhotoUrl = state.selfPhotoUrl)
        when (signIn) {
            DuelSignIn.Offline -> SignInProblem("No connection, so sign-in didn't finish. Try again when you're online.")
            DuelSignIn.Failed -> SignInProblem("Sign-in didn't finish. Please try again.")
            DuelSignIn.Idle, DuelSignIn.SigningIn -> Unit
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton(
                text = if (signingIn) "Signing in…" else SIGN_IN_WITH_GOOGLE,
                onClick = actions.onGoogle,
                enabled = !signingIn,
                modifier = Modifier.fillMaxWidth().testTag("duel.signIn.google"),
            )
            SecondaryButton(text = "Cancel", onClick = actions.onCancel, modifier = Modifier.fillMaxWidth().testTag("duel.signIn.cancel"))
        }
        Text(
            text = "Friends' puzzles and your dailies stay right here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignInProblem(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("duel.signIn.problem"))
}

package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.create.editor.PublishSession.SignInFailure
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * Sign in to publish (08 handoff "Sign in to publish"): why an account, Continue with Google
 * ([onGoogle]; "Signing in…" and disabled while [PublishSession.SignIn.signingIn]) and Cancel
 * ([onCancel], as is a swipe or the scrim). Open while [signIn] is non-null; when it turns null
 * the sheet slides away with its last content.
 *
 * Motion: the sheet's **Slide in / Slide out** and the scrim's **Fade** are ModalBottomSheet's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignInPublishSheet(
    signIn: PublishSession.SignIn?,
    onGoogle: () -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var last by remember { mutableStateOf(signIn) }
    SideEffect { if (signIn != null) last = signIn }
    LaunchedEffect(signIn == null) {
        if (signIn != null) return@LaunchedEffect
        sheetState.hide()
        last = null
    }
    val shown = signIn ?: last ?: return
    ModalBottomSheet(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("create.signIn.sheet"),
        sheetState = sheetState,
    ) {
        SignInPublishContent(signingIn = shown.signingIn, onGoogle = onGoogle, onCancel = onCancel)
    }
}

/**
 * The sign-in [failure]'s reason as a snackbar, tagged `create.signIn.failure.<reason>`. A
 * timeout or swipe reports [onGone]; [failure] dropping (the next publish) removes it at once.
 */
@Composable
internal fun SignInFailureToast(failure: SignInFailure?, onGone: () -> Unit, modifier: Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(failure) {
        val shown = failure ?: return@LaunchedEffect
        snackbar.showSnackbar(FailureVisuals(message = messageOf(shown), tag = "create.signIn.failure.${keyOf(shown)}"))
        onGone()
    }
    SnackbarHost(snackbar, modifier) { data ->
        Snackbar(data, Modifier.testTag((data.visuals as? FailureVisuals)?.tag ?: "create.signIn.failure"))
    }
}

@Composable
private fun SignInPublishContent(signingIn: Boolean, onGoogle: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Sign in to publish", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "Publishing ties the puzzle to you, so only you can unpublish it. Friends never need an account to play.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton(
                text = if (signingIn) "Signing in…" else "Continue with Google",
                onClick = onGoogle,
                enabled = !signingIn,
                modifier = Modifier.fillMaxWidth().testTag("create.signIn.google"),
            )
            SecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth().testTag("create.signIn.cancel"))
        }
        Text(
            text = "Your draft and test solve stay right here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Carries the reason's tag to the snackbar it shows. */
private data class FailureVisuals(override val message: String, val tag: String) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Long
}

private fun keyOf(failure: SignInFailure): String = when (failure) {
    is SignInFailure.Token -> failure.reason.name.lowercase()
    is SignInFailure.Account -> when (failure.error) {
        is AuthError.Unavailable -> "account.unavailable"
        is AuthError.Rejected -> "account.rejected"
        is AuthError.Malformed -> "account.malformed"
        is AuthError.Unknown -> "account.unknown"
    }
}

/** Only a failure that never reached Google or the account backend blames the connection. */
private fun messageOf(failure: SignInFailure): String = when (failure) {
    is SignInFailure.Token -> when (failure.reason) {
        GoogleIdToken.Unavailable.Reason.NETWORK -> "No connection, so Google couldn't sign you in. Nothing was published."
        GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL -> "No Google account on this device. Add one in Settings to publish."
        GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE -> "Google sign-in isn't available on this device right now."
        GoogleIdToken.Unavailable.Reason.CANCELLED -> "Sign-in cancelled. Nothing was published."
        GoogleIdToken.Unavailable.Reason.MALFORMED,
        GoogleIdToken.Unavailable.Reason.OTHER,
        -> "Google sign-in didn't finish. Please try again."
    }
    is SignInFailure.Account -> when (failure.error) {
        is AuthError.Unavailable -> "Couldn't reach your account. Check your connection and try again."
        is AuthError.Rejected -> "Google couldn't confirm this account. Try again, or pick another."
        is AuthError.Malformed -> "The account response was unusable. Please try again."
        is AuthError.Unknown -> "Sign-in failed unexpectedly. Please try again."
    }
}

/** The sheet's body alone: ModalBottomSheet draws in its own window, which some panes skip. */
@PuzzleBoxPreviews
@Composable
private fun SignInPublishContentPreview() {
    PuzzleBoxTheme {
        SignInPublishContent(signingIn = false, onGoogle = {}, onCancel = {})
    }
}

/** While Google answers: the button reads "Signing in…" and is disabled. */
@PuzzleBoxPreviews
@Composable
private fun SignInPublishContentSigningInPreview() {
    PuzzleBoxTheme {
        SignInPublishContent(signingIn = true, onGoogle = {}, onCancel = {})
    }
}

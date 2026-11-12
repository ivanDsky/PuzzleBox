package com.ivandsky.puzzlebox.profile.linkedin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.profile.linkedin.LinkedInLoginViewModel.Step

/**
 * The LinkedIn login, a pushed tool screen: a compact top bar with Back over the body for
 * [step]. The web view exists only while the step is [Step.SignIn], so nothing loads before the
 * warning is accepted. [onBack] also serves both Cancel buttons. [savedToAccount] (cloud sync is on) switches the
 * warning's copy about the connections' boards.
 */
@Composable
internal fun LinkedInLoginScreen(
    step: Step,
    onAccept: () -> Unit,
    onPageFinished: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    savedToAccount: Boolean = false,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompactTopBar(
            title = "Connect LinkedIn",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("linkedin.login.back")) {
                    Icon(
                        imageVector = PuzzleIcons.Back,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        when (step) {
            Step.Warning -> LinkedInRiskWarning(
                onAccept = onAccept,
                onCancel = onBack,
                modifier = Modifier.fillMaxSize(),
                savedToAccount = savedToAccount,
            )
            Step.SignIn -> LinkedInLoginWebView(onPageFinished = onPageFinished, modifier = Modifier.fillMaxSize())
            Step.Checking -> Checking()
            is Step.Failed -> Failed(message = step.message, onRetry = onRetry, onCancel = onBack)
            // The route leaves as soon as it sees Done; nothing is drawn for that last frame.
            Step.Done -> Unit
        }
    }
}

@Composable
private fun Checking() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl)
            .semantics(mergeDescendants = true) {}
            .testTag("linkedin.login.checking"),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = "Checking your LinkedIn login…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("linkedin.login.error"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            text = "Try again",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().testTag("linkedin.login.retry"),
        )
        SecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

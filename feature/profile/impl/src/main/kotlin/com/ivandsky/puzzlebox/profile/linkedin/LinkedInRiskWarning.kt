package com.ivandsky.puzzlebox.profile.linkedin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

// The copy lives in constants so a wording change touches only these lines. It names LinkedIn on
// purpose: the player must know exactly whose terms are in play before connecting.
private const val Title = "Connect LinkedIn?"
private const val AcceptLabel = "Accept and continue"
private const val CancelLabel = "Cancel"
private const val Bullet = "•"

// With cloud sync on, the connections' boards are no longer phone-only: the warning says where they go and who reads them.
private const val BoardsOnPhone = "Your connections' times stay on this phone and are never uploaded."
private const val BoardsInAccount =
    "Your connections' boards are saved to your own PuzzleBox account while you're signed in with Google. " +
        "Only you can read them, and they are kept after Disconnect."

private fun points(savedToAccount: Boolean) = listOf(
    "Reading your results this way is not allowed by LinkedIn's User Agreement (§8.2). " +
        "LinkedIn could limit or close your account. The risk is yours.",
    "It only reads. PuzzleBox never posts, submits or changes anything on LinkedIn.",
    if (savedToAccount) BoardsInAccount else BoardsOnPhone,
    "Your LinkedIn password is typed into LinkedIn's own page; PuzzleBox never sees it. " +
        "The login stays in this phone's web view.",
    "Disconnect clears this phone's login. It does not sign you out of LinkedIn anywhere else.",
)

/**
 * The warning shown before every LinkedIn connect: the user-agreement breach and the four facts
 * about what PuzzleBox does and does not do, then Accept or Cancel. With cloud sync on ([savedToAccount]) the
 * fact about the connections' boards says they are saved to the player's own account instead of staying on the
 * phone. Stateless and side-effect free; no web view exists until the player taps Accept.
 */
@Composable
internal fun LinkedInRiskWarning(
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    savedToAccount: Boolean = false,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .testTag("linkedin.warning"),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = Title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            points(savedToAccount).forEach { Point(it) }
        }
        PrimaryButton(
            text = AcceptLabel,
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().testTag("linkedin.warning.accept"),
        )
        SecondaryButton(
            text = CancelLabel,
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().testTag("linkedin.warning.cancel"),
        )
    }
}

@Composable
private fun Point(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = Bullet,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun LinkedInRiskWarningPreview() {
    PuzzleBoxTheme {
        LinkedInRiskWarning(onAccept = {}, onCancel = {})
    }
}

@PuzzleBoxPreviews
@Composable
internal fun LinkedInRiskWarningSavedToAccountPreview() {
    PuzzleBoxTheme {
        LinkedInRiskWarning(onAccept = {}, onCancel = {}, savedToAccount = true)
    }
}

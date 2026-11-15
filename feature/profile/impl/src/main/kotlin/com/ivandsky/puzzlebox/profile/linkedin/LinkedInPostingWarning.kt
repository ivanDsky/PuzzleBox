package com.ivandsky.puzzlebox.profile.linkedin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.DialogCard
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

// The copy lives in constants so a wording change touches only these lines. It says out loud what the connect
// warning does not: posting writes to LinkedIn, so it is more visible and cannot be taken back.
private const val Title = "Post results to LinkedIn?"
private const val AcceptLabel = "Accept and turn on"
private const val CancelLabel = "Cancel"
private const val Bullet = "•"

// Past this the points scroll, so the buttons stay on screen at large font sizes.
private val BodyMaxHeight = 320.dp

private val points = listOf(
    "PuzzleBox will send your result to LinkedIn as if you played there.",
    "This breaks LinkedIn's terms more visibly than reading does.",
    "Who sees it is up to your LinkedIn settings. PuzzleBox never changes them.",
    "A post can't be undone.",
    "LinkedIn may restrict your account. The risk is yours.",
)

/**
 * The warning shown before posting is turned on: what posting does and what it risks, then Accept or Cancel.
 * Only [onAccept] turns posting on; [onCancel] (also the scrim and Back) changes nothing. Stateless.
 */
@Composable
internal fun LinkedInPostingWarning(onAccept: () -> Unit, onCancel: () -> Unit) {
    AppDialog(
        title = Title,
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("linkedin.posting.warning"),
        content = { PostingWarningBody() },
        actions = { PostingWarningActions(onAccept, onCancel) },
    )
}

@Composable
private fun PostingWarningBody() {
    Column(
        modifier = Modifier.heightIn(max = BodyMaxHeight).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        points.forEach { Point(it) }
    }
}

@Composable
private fun PostingWarningActions(onAccept: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTextButton(text = CancelLabel, onClick = onCancel, modifier = Modifier.testTag("linkedin.posting.warning.cancel"))
        PrimaryButton(text = AcceptLabel, onClick = onAccept, modifier = Modifier.testTag("linkedin.posting.warning.accept"))
    }
}

@Composable
private fun Point(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = Bullet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// The Studio canvas does not render AppDialog's platform window, so the preview composes the card it hosts
// (DialogCard, as the design system's AppDialogPreview does), minus scrim and entrance.
@PuzzleBoxPreviews
@Composable
internal fun LinkedInPostingWarningPreview() {
    PuzzleBoxTheme {
        // The Box lets the card wrap its content when a parent surface forces its size to the whole screen.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.padding(horizontal = Spacing.xxl).widthIn(max = 380.dp).fillMaxWidth()) {
                Text(text = Title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                PostingWarningBody()
                PostingWarningActions(onAccept = {}, onCancel = {})
            }
        }
    }
}

package com.ivandsky.puzzlebox.profile.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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

private const val TITLE = "Delete my cloud data?"
private const val BODY =
    "This removes your synced history and saved boards from your account and signs you out. " +
        "Everything stays on this phone. It can't be undone."

/**
 * The destructive confirm for Delete my cloud data: Cancel ([onCancel], also the scrim and Back) changes nothing;
 * Delete ([onConfirm]) asks the engine to wipe the account's cloud space.
 */
@Composable
internal fun DeleteCloudDataDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AppDialog(
        title = TITLE,
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("profile.google.delete.dialog"),
        content = { DeleteCloudDataBody() },
        actions = { DeleteCloudDataActions(onConfirm, onCancel) },
    )
}

@Composable
private fun DeleteCloudDataBody() {
    Text(text = BODY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DeleteCloudDataActions(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTextButton(text = "Cancel", onClick = onCancel, modifier = Modifier.testTag("profile.google.delete.cancel"))
        PrimaryButton(text = "Delete", onClick = onConfirm, destructive = true, modifier = Modifier.testTag("profile.google.delete.confirm"))
    }
}

// The Studio canvas does not render AppDialog's platform window, so the preview composes the card it hosts
// (DialogCard, as the design system's AppDialogPreview does), minus scrim and entrance.
@PuzzleBoxPreviews
@Composable
internal fun DeleteCloudDataDialogPreview() {
    PuzzleBoxTheme {
        DialogCard(Modifier.padding(horizontal = Spacing.xxl).widthIn(max = 380.dp).fillMaxWidth()) {
            Text(text = TITLE, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            DeleteCloudDataBody()
            DeleteCloudDataActions(onConfirm = {}, onCancel = {})
        }
    }
}

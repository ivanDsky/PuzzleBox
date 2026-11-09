package com.ivandsky.puzzlebox.create.editor

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

/**
 * The resize confirm ([ResizeSession]): switching to [sizeLabel] clears a board with content.
 * Cancel ([onCancel], also the scrim and Back) changes nothing; Start blank ([onConfirm]) is a
 * plain primary action, not a destructive one, as Undo brings the board back. The card scales
 * in from 0.95 with a fade (AppDialog's own entrance).
 */
@Composable
internal fun ResizeConfirmDialog(sizeLabel: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AppDialog(
        title = ResizeConfirmTitle,
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("create.resize.dialog"),
        content = { ResizeConfirmBody(sizeLabel) },
        actions = { ResizeConfirmActions(onConfirm, onCancel) },
    )
}

private const val ResizeConfirmTitle = "Start a blank board?"

@Composable
private fun ResizeConfirmBody(sizeLabel: String) {
    Text(
        text = "Switching to $sizeLabel clears this board. Undo brings it back.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ResizeConfirmActions(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTextButton(text = "Cancel", onClick = onCancel, modifier = Modifier.testTag("create.resize.cancel"))
        PrimaryButton(text = "Start blank", onClick = onConfirm, modifier = Modifier.testTag("create.resize.confirm"))
    }
}

// The Studio canvas does not render AppDialog's platform window, so the preview composes the
// card it hosts (DialogCard, as Dialogs.kt's AppDialogPreview does), minus scrim and entrance.
@PuzzleBoxPreviews
@Composable
private fun ResizeConfirmDialogPreview() {
    PuzzleBoxTheme {
        DialogCard(modifier = Modifier.padding(horizontal = Spacing.xxl).widthIn(max = 380.dp).fillMaxWidth()) {
            Text(
                text = ResizeConfirmTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ResizeConfirmBody(sizeLabel = "8×8")
            ResizeConfirmActions(onConfirm = {}, onCancel = {})
        }
    }
}

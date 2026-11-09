package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.AppTextButton
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
        title = "Start a blank board?",
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("create.resize.dialog"),
        content = {
            Text(
                text = "Switching to $sizeLabel clears this board. Undo brings it back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(text = "Cancel", onClick = onCancel, modifier = Modifier.testTag("create.resize.cancel"))
                PrimaryButton(text = "Start blank", onClick = onConfirm, modifier = Modifier.testTag("create.resize.confirm"))
            }
        },
    )
}

@PuzzleBoxPreviews
@Composable
private fun ResizeConfirmDialogPreview() {
    PuzzleBoxTheme {
        ResizeConfirmDialog(sizeLabel = "8×8", onConfirm = {}, onCancel = {})
    }
}

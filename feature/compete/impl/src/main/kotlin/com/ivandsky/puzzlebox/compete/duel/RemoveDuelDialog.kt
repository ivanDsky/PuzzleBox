package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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

private const val TITLE = "Remove this duel?"
private const val BODY = "Your solves stay saved."
private val KeepBorder = 2.dp

/** The confirm for removing a self duel. Keep, the scrim and Back leave it as it was; Remove deletes it. */
@Composable
internal fun RemoveDuelDialog(onRemove: () -> Unit, onKeep: () -> Unit) {
    AppDialog(
        title = TITLE,
        onDismissRequest = onKeep,
        modifier = Modifier.testTag("duel.remove.dialog"),
        content = { RemoveDuelBody() },
        actions = { RemoveDuelActions(onRemove, onKeep) },
    )
}

@Composable
private fun RemoveDuelBody() {
    Text(text = BODY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RemoveDuelActions(onRemove: () -> Unit, onKeep: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTextButton(
            text = "Keep",
            onClick = onKeep,
            modifier = Modifier.border(KeepBorder, MaterialTheme.colorScheme.primary, CircleShape).testTag("duel.remove.keep"),
        )
        PrimaryButton(text = "Remove", onClick = onRemove, destructive = true, modifier = Modifier.testTag("duel.remove.confirm"))
    }
}

// The Studio canvas does not render AppDialog's platform window, so the preview composes the card it hosts.
@PuzzleBoxPreviews
@Composable
private fun RemoveDuelDialogPreview() {
    PuzzleBoxTheme {
        DialogCard(modifier = Modifier.padding(horizontal = Spacing.xxl).widthIn(max = 380.dp).fillMaxWidth()) {
            Text(text = TITLE, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            RemoveDuelBody()
            RemoveDuelActions(onRemove = {}, onKeep = {})
        }
    }
}

package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.games.strandsui.HintDialog
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy

/** The two A7 hint modals: NYT's first sentence as the title, the rest as the body. */
@Composable
internal fun HintModal(dialog: HintDialog, onDismiss: () -> Unit) {
    val (title, body) = when (dialog) {
        HintDialog.OnBoard -> StrandsCopy.HINT_ON_BOARD_TITLE to StrandsCopy.HINT_ON_BOARD_BODY
        HintDialog.NoneAvailable -> StrandsCopy.HINT_NONE_AVAILABLE_TITLE to StrandsCopy.HINT_NONE_AVAILABLE_BODY
    }
    AppDialog(
        title = title,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("strands.hint_dialog"),
        content = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        actions = {
            PrimaryButton(
                text = StrandsCopy.DISMISS,
                onClick = onDismiss,
                modifier = Modifier.testTag("strands.hint_dialog_dismiss"),
            )
        },
    )
}

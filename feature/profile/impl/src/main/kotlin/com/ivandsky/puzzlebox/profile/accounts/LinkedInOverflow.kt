package com.ivandsky.puzzlebox.profile.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

private val MenuMinWidth = 180.dp
private val MenuItemHeight = 48.dp

/**
 * The LinkedIn row's options: Disconnect, offered whenever the link exists. It acts at once with no confirmation,
 * because connecting again undoes it and the imports and saved boards are kept. With cloud boards bound
 * ([onDeleteSavedBoards] given) it also offers Delete saved boards, which asks first in [DeleteSavedBoardsDialog] and
 * calls [onDeleteSavedBoards] only on its Delete; not offered while one is running ([deleteEnabled] false).
 */
@Composable
internal fun LinkedInOverflow(
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteSavedBoards: (() -> Unit)? = null,
    deleteEnabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("profile.linkedin.overflow")) {
            Icon(
                imageVector = PuzzleIcons.More,
                contentDescription = "LinkedIn options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { DestructiveLabel("Disconnect LinkedIn") },
                onClick = {
                    open = false
                    onDisconnect()
                },
                modifier = Modifier.defaultMinSize(minWidth = MenuMinWidth, minHeight = MenuItemHeight).testTag("profile.linkedin.disconnect"),
            )
            if (onDeleteSavedBoards != null) {
                DropdownMenuItem(
                    text = { DestructiveLabel("Delete saved boards") },
                    onClick = {
                        open = false
                        confirming = true
                    },
                    enabled = deleteEnabled,
                    modifier = Modifier.defaultMinSize(minWidth = MenuMinWidth, minHeight = MenuItemHeight).testTag("profile.linkedin.delete-boards"),
                )
            }
        }
    }
    if (confirming && onDeleteSavedBoards != null) {
        DeleteSavedBoardsDialog(
            onConfirm = {
                confirming = false
                onDeleteSavedBoards()
            },
            onCancel = { confirming = false },
        )
    }
}

@Composable
private fun DestructiveLabel(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
}

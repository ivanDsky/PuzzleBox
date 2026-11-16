package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

private val TouchTarget = 48.dp

/** The friend card's overflow: a 48dp ⋮ that opens a menu with Report ([onReport]). */
@Composable
internal fun FriendCardMenu(onReport: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(TouchTarget).testTag("friend.card.more")) {
            Icon(PuzzleIcons.More, contentDescription = "Puzzle actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Report", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onReport()
                },
                modifier = Modifier.testTag("friend.card.report"),
            )
        }
    }
}

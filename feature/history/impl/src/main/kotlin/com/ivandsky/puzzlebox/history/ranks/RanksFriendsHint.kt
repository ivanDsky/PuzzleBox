package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing

/** What a Friends board with only the viewer on it says to do next, set under the board so the list is never a bare one. Plain text: it is not a button. */
@Composable
internal fun RanksFriendsHint(modifier: Modifier = Modifier) {
    Text(
        "Duel someone or connect LinkedIn to fill Friends",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .testTag("ranks.board.friendsHint")
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
    )
}

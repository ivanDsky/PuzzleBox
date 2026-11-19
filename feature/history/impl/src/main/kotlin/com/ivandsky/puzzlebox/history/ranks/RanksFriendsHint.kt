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
internal fun RanksFriendsHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .testTag("ranks.board.friendsHint")
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
    )
}

/** LinkedIn connections fill Friends only on a LinkedIn game's board; on any other the only way in is a duel. */
internal fun friendsHintText(withLinkedIn: Boolean): String =
    if (withLinkedIn) "Duel someone or connect LinkedIn to fill Friends" else "Duel someone to fill Friends"

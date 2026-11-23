package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * A stand-in for the app's slot, whose Yours list is create's: New puzzle over [count] drafts, or with none the
 * first-time message. The Yours page's tests and goldens share it.
 */
internal fun previewYoursSlot(count: Int) = CustomSlot(
    yours = { listState ->
        if (count == 0) {
            EmptyState("Make a puzzle for someone", "Pick a game, build the board, and share a short code.")
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item { PrimaryButton("New puzzle", onClick = {}, modifier = Modifier.fillMaxWidth()) }
                item { FriendsSectionHead("Continue", count) }
                items(count) { Text("Draft ${it + 1}", style = MaterialTheme.typography.titleMedium) }
            }
        }
    },
)

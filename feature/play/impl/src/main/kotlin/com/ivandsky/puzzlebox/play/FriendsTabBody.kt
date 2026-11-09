package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The Friends tab: one full-width [FriendCard] per friend puzzle, no section head (the pill
 * carries the count). With none, an empty state offers [onEnterCode]. A card opens through
 * [onOpen]; its long-press menu removes through [onRemove].
 */
@Composable
internal fun FriendsTabBody(
    cards: List<FriendCardModel>,
    onOpen: (ShareCode) -> Unit,
    onRemove: (ShareCode) -> Unit,
    onEnterCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) {
        Column(
            modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(title = "No puzzles from friends yet", message = "Puzzles friends send you show up here.")
            SecondaryButton("Enter code", onClick = onEnterCode, modifier = Modifier.testTag("library.friends.enter_code"))
        }
        return
    }
    val reduced = rememberReducedMotion()
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        cards.forEach { card ->
            FriendCard(
                card = card,
                reduced = reduced,
                onClick = { onOpen(card.row.code) },
                onRemove = { onRemove(card.row.code) },
                modifier = Modifier.fillMaxWidth(),
                fillWidth = true,
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun FriendsTabBodyPreview() {
    PuzzleBoxTheme {
        Surface { FriendsTabBody(FriendPreviewCards, onOpen = {}, onRemove = {}, onEnterCode = {}) }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun FriendsTabBodyEmptyPreview() {
    PuzzleBoxTheme {
        Surface { FriendsTabBody(emptyList(), onOpen = {}, onRemove = {}, onEnterCode = {}) }
    }
}

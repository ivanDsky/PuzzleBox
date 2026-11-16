package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
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
        FriendsEmptyHint(onEnterCode, modifier)
        return
    }
    val reduced = rememberReducedMotion()
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        cards.forEach { card -> FriendRow(card, reduced, onOpen, onRemove) }
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

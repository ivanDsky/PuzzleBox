package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * From friends as rows of a list that is someone else's (the Custom tab's): the section head
 * with its count, then [friendRows]. [reduced] is the caller's reduced-motion read, taken once
 * for all the cards.
 */
internal fun LazyListScope.fromFriendsSection(
    cards: List<FriendCardModel>,
    reduced: Boolean,
    onOpen: (ShareCode) -> Unit,
    onRemove: (ShareCode) -> Unit,
    onEnterCode: () -> Unit,
) {
    item(key = "friend:head", contentType = "friend.head") {
        FriendsSectionHead(
            title = "From friends",
            count = cards.size.takeIf { it > 0 },
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.lg, bottom = Spacing.sm),
        )
    }
    friendRows(cards, reduced, onOpen, onRemove, onEnterCode)
}

/**
 * One [FriendRow] per puzzle in [cards]; with none, the hint that offers [onEnterCode].
 * Every row pads its own sides and carries its own vertical spacing, so the list needs neither;
 * the keys start with `friend:`. A row opens through [onOpen]; its long-press menu removes
 * through [onRemove].
 */
internal fun LazyListScope.friendRows(
    cards: List<FriendCardModel>,
    reduced: Boolean,
    onOpen: (ShareCode) -> Unit,
    onRemove: (ShareCode) -> Unit,
    onEnterCode: () -> Unit,
) {
    if (cards.isEmpty()) {
        item(key = "friend:empty", contentType = "friend.empty") { FriendsEmptyHint(onEnterCode) }
        return
    }
    items(cards, key = { "friend:${it.row.code.value}" }, contentType = { "friend.card" }) { card ->
        FriendRow(card, reduced, onOpen, onRemove, Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.md))
    }
}

/** A section head: [title] with an optional [count] beside it. */
@Composable
internal fun FriendsSectionHead(title: String, count: Int?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs).testTag("library.friends.count"),
            )
        }
    }
}

/** What shows for no friend puzzles: the hint and an Enter code button ([onEnterCode]). */
@Composable
internal fun FriendsEmptyHint(onEnterCode: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(title = "No puzzles from friends yet", message = "Puzzles friends send you show up here.")
        SecondaryButton("Enter code", onClick = onEnterCode, modifier = Modifier.testTag("library.friends.enter_code"))
    }
}

@PuzzleBoxPreviews
@Composable
internal fun FromFriendsSectionPreview() {
    PuzzleBoxTheme {
        Surface {
            LazyColumn { fromFriendsSection(FriendPreviewCards.take(2), reduced = true, onOpen = {}, onRemove = {}, onEnterCode = {}) }
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun FromFriendsSectionEmptyPreview() {
    PuzzleBoxTheme {
        Surface {
            LazyColumn { fromFriendsSection(emptyList(), reduced = true, onOpen = {}, onRemove = {}, onEnterCode = {}) }
        }
    }
}

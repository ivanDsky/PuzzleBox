package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

// Previews of the community cards at the handoff dims: light and dark come from @PuzzleBoxPreviews;
// the "longest" variants carry the two-line titles, long author and four-digit solves.

private const val LONG_TITLE = "The very long name of a puzzle that wraps over two lines and then keeps going"
private const val LONG_AUTHOR = "An author with an extremely long display name"

/** The page surface the cards sit on in the app. */
@Composable
private fun CardPreviewFrame(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) { content() }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ShelfCardBadgesPreview() = CardPreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ShelfCard("Spiral staircase", "Mia", 132, AccentSlot.Blue, GameGlyphs.Zip, onClick = {}, difficulty = "Hard", badge = ShelfBadge.New)
        ShelfCard("Garden path", "Tomas", 48, AccentSlot.Violet, GameGlyphs.Queens, onClick = {}, difficulty = "Easy", badge = ShelfBadge.Friend)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ShelfCard("Sun and moon", "You", 7, AccentSlot.Orange, GameGlyphs.Tango, onClick = {}, badge = ShelfBadge.You)
        ShelfCard("Word thread", "Ana", 1203, AccentSlot.Pink, GameGlyphs.Strands, onClick = {}, difficulty = "Medium", selected = true)
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ShelfCardLongestPreview() = CardPreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ShelfCard(LONG_TITLE, LONG_AUTHOR, 9876, AccentSlot.Teal, GameGlyphs.Sudoku, onClick = {}, difficulty = "Medium", badge = ShelfBadge.Friend)
        MakePuzzleCard("Mini Sudoku", onClick = {})
    }
}

@PuzzleBoxPreviews
@Composable
internal fun SpotlightCardPreview() = CardPreviewFrame {
    SpotlightCard("Spiral staircase", "Zip", "Mia", 4120, AccentSlot.Blue, GameGlyphs.Zip, onClick = {}, difficulty = "Hard")
    SpotlightCard("Garden path", "Queens", "Tomas", 980, AccentSlot.Violet, GameGlyphs.Queens, onClick = {}, selected = true)
}

@PuzzleBoxPreviews
@Composable
internal fun SpotlightCardLongestPreview() = CardPreviewFrame {
    SpotlightCard(LONG_TITLE, "Mini Sudoku", LONG_AUTHOR, 12345, AccentSlot.Teal, GameGlyphs.Sudoku, onClick = {}, difficulty = "Medium")
}

@PuzzleBoxPreviews
@Composable
internal fun EntryTilesPreview() = CardPreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        EntryTile("From friends", "7 puzzles", onClick = {}, modifier = Modifier.weight(1f)) {
            AuthorAvatars(listOf('M', 'T', 'A'))
        }
        EntryTile("Your puzzles", "4 published · 2 drafts", onClick = {}, modifier = Modifier.weight(1f)) {
            PreviewYouAvatar()
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun EntryTilesLongestPreview() = CardPreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        EntryTile("From friends and everyone they know", "1,284 puzzles from 36 friends", onClick = {}, modifier = Modifier.weight(1f)) {
            AuthorAvatars(listOf('M', 'T', 'A', 'B'))
        }
        EntryTile("Your puzzles", "12 published · 30 drafts", onClick = {}, modifier = Modifier.weight(1f)) {
            PreviewYouAvatar()
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun MakePuzzleCardsPreview() = CardPreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        MakePuzzleCard("Zip", onClick = {})
        MakePuzzleCard("Queens", onClick = {})
    }
    MakePuzzleCard("Mini Sudoku", onClick = {}, width = ShelfCardDefaults.MakeRowCardWidth)
}

@PuzzleBoxPreviews
@Composable
internal fun AuthorAvatarsPreview() = CardPreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        AuthorAvatars(listOf('M'))
        AuthorAvatars(listOf('M', 'T'))
        AuthorAvatars(listOf('M', 'T', 'A', 'B'))
    }
}

/** The "you" lead of the Your puzzles tile: the tertiary disc the app's own avatar wears. */
@Composable
private fun PreviewYouAvatar() {
    Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Text("I", color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
    }
}

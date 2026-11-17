package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular

/** The card's time tiles: the author's time and hints, beside yours once solved. */
@Composable
internal fun Stats(card: FriendCard) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Tile("Author", Modifier.weight(1f)) {
            Value(formatDuration(card.puzzle.authorTimeMs), "friend.card.author.time")
            Hints(card.puzzle.authorHints, "friend.card.author.hints")
        }
        card.yours?.let {
            Tile("Your time", Modifier.weight(1f)) {
                Value(formatDuration(it.timeMs), "friend.card.solved")
                Hints(it.hints, "friend.card.solved.hints")
            }
        }
    }
}

@Composable
private fun Tile(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun Value(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.tabular().copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun Hints(hints: Int, tag: String) {
    Text(
        text = hintsLabel(hints),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(tag),
    )
}

private fun hintsLabel(hints: Int) = if (hints == 1) "1 hint" else "$hints hints"

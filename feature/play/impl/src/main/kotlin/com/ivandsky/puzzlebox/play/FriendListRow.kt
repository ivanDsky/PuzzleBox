package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.LocalSelectedRowCode
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.selectedRow
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.ShareCode

private val RowMinHeight = 72.dp

/**
 * One friend puzzle as a row of a vertical list, shaped like the community list's row: the game's glyph over
 * "Game · from a friend", the title (up to two lines, no reserved second), then "by author"; the status chip
 * sits at the end, centred, where the community row has its ⋮. The whole row opens [onOpen]; a long press
 * offers Remove ([onRemove]) in a menu at the row. The carousel's [FriendCard] is the fixed-width, equal-height
 * shape for a horizontal list.
 */
@Composable
internal fun FriendRow(
    card: FriendCardModel,
    reduced: Boolean,
    onOpen: (ShareCode) -> Unit,
    onRemove: (ShareCode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val code = card.row.code.value
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Surface(
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.large)
                .selectedRow(LocalSelectedRowCode.current == code)
                .combinedClickable(
                    onClick = { onOpen(card.row.code) },
                    onLongClickLabel = "Puzzle actions",
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .testTag("library.friends.row.$code"),
        ) {
            Row(
                Modifier.heightIn(min = RowMinHeight).padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        GameGlyph(card.brand, GameGlyphSize.Chip)
                        Text(
                            text = "${card.gameName} · from a friend",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = card.row.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "by ${card.row.authorName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                StatusChip(code, card.row.status, reduced)
            }
        }
        AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    onRemove(card.row.code)
                },
                modifier = Modifier.testTag("library.friends.$code.remove"),
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun FriendRowStatesPreview() {
    PuzzleBoxTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FriendPreviewCards.forEach { FriendRow(it, reduced = true, onOpen = {}, onRemove = {}) }
            }
        }
    }
}

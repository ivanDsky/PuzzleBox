package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.LocalSelectedRowCode
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.selectedRow
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.play.domain.FriendStatus

private val CardWidth = 256.dp

/**
 * One friend's puzzle: game glyph and name, its title over "by <author>", and its status chip;
 * the whole card is the target. A long press offers Remove ([onRemove]) in a menu at the card.
 * Fixed width, so every card in the carousel lines up; the list's [FriendRow] is its own shape.
 */
@Composable
internal fun FriendCard(
    card: FriendCardModel,
    reduced: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val code = card.row.code.value
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .width(CardWidth)
                .clip(AppShapes.large)
                .selectedRow(LocalSelectedRowCode.current == code)
                .combinedClickable(
                    onClick = onClick,
                    onLongClickLabel = "Puzzle actions",
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .testTag("library.friends.row.$code"),
        ) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                val titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                val bylineStyle = MaterialTheme.typography.bodyMedium
                // Room for a two-line title over the byline, so every card in the carousel is the
                // same height while a one-line title keeps its byline right under it.
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val textBlock = remember(titleStyle, bylineStyle, density) {
                    val lines = measurer.measure("\n", titleStyle).size.height + measurer.measure("", bylineStyle).size.height
                    with(density) { lines.toDp() } + Spacing.xs
                }
                Column(Modifier.heightIn(min = textBlock), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = card.row.title,
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "by ${card.row.authorName}",
                        style = bylineStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                StatusChip(code, card.row.status, reduced)
            }
        }
        AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
                modifier = Modifier.testTag("library.friends.$code.remove"),
            )
        }
    }
}

private fun previewCard(code: String, game: String, brand: GameBrand, title: String, author: String, status: FriendStatus) =
    FriendCardModel(
        row = FriendPuzzleRow(checkNotNull(ShareCode.parse(code)), GameId(game.lowercase()), title, author, status),
        gameName = game,
        brand = brand,
    )

internal val FriendPreviewCards = listOf(
    previewCard("K7Q2MX", "Tango", GameBrand(AccentSlot.Orange, GameGlyphs.Tango), "Sunday sun and moons", "Jonas", FriendStatus.InProgress(70_000)),
    previewCard(
        "B4R9TZ", "Queens", GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
        "Rule of the castle, a long title that wraps past two lines", "Mia", FriendStatus.Solved(228_000),
    ),
    previewCard("M3N8PQ", "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip), "One line", "Ari", FriendStatus.TakenDown),
    previewCard("H2J5WX", "Queens", GameBrand(AccentSlot.Violet, GameGlyphs.Queens), "Fresh one", "Bo", FriendStatus.New),
    previewCard("Z9Y8XW", "Gone", GameBrand.Neutral, "From a newer build", "Lee", FriendStatus.NeedsUpdate),
)

@PuzzleBoxPreviews
@Composable
internal fun FriendCardStatesPreview() {
    PuzzleBoxTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FriendPreviewCards.forEach { FriendCard(it, reduced = true, onClick = {}, onRemove = {}) }
            }
        }
    }
}

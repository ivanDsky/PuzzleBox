package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.LocalMutedStatus
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.domain.FriendStatus
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

private val CardWidth = 256.dp
private val ChipHeight = 28.dp
private val ChipIcon = 16.dp

// A status change pops the chip in from a little smaller, on the stamp's spring.
private const val CHIP_POP_FROM_SCALE = 0.85f

/**
 * One friend's puzzle: game glyph and name, its title over "by <author>", and its status chip;
 * the whole card is the target. A long press offers Remove ([onRemove]) in a menu at the card.
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
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = card.row.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        // Two lines always, so every card in the carousel is the same height.
                        minLines = 2,
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
                Spacer(Modifier.height(Spacing.xs))
                StatusChip(code, card.row.status, reduced)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = AppShapes.medium) {
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

@Composable
private fun StatusChip(code: String, status: FriendStatus, reduced: Boolean) {
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(MotionTokens.ReducedFadeDuration)) togetherWith fadeOut(tween(MotionTokens.ReducedFadeDuration))
            } else {
                (
                    fadeIn(tween(MotionTokens.StampFadeDuration)) +
                        scaleIn(spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness), CHIP_POP_FROM_SCALE)
                    ) togetherWith fadeOut(tween(MotionTokens.StampFadeDuration))
            }
        },
        contentAlignment = Alignment.CenterStart,
        label = "friend-status",
    ) { shown ->
        val look = chipLook(shown)
        Surface(
            shape = PillShape,
            color = look.container,
            contentColor = look.content,
            modifier = Modifier.height(ChipHeight).testTag("library.friends.$code.status.${shown.tag}"),
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (shown is FriendStatus.Solved) Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(ChipIcon))
                Text(shown.label, style = MaterialTheme.typography.labelLarge.tabular(), maxLines = 1)
            }
        }
    }
}

private class ChipLook(val container: Color, val content: Color)

@Composable
private fun chipLook(status: FriendStatus): ChipLook {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        FriendStatus.New -> ChipLook(scheme.secondaryContainer, scheme.onSecondaryContainer)
        is FriendStatus.InProgress -> ChipLook(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        is FriendStatus.Solved -> ChipLook(scheme.primaryContainer, scheme.onPrimaryContainer)
        FriendStatus.TakenDown -> LocalMutedStatus.current.let { ChipLook(it.container, it.content) }
        FriendStatus.NeedsUpdate -> ChipLook(scheme.errorContainer, scheme.onErrorContainer)
    }
}

private val FriendStatus.label: String
    get() = when (this) {
        FriendStatus.New -> "New"
        is FriendStatus.InProgress -> "In progress · ${formatDuration(elapsedMs)}"
        is FriendStatus.Solved -> "Solved ${formatDuration(timeMs)}"
        FriendStatus.TakenDown -> "Taken down · still plays"
        FriendStatus.NeedsUpdate -> "Needs update"
    }

private val FriendStatus.tag: String
    get() = when (this) {
        FriendStatus.New -> "new"
        is FriendStatus.InProgress -> "in_progress"
        is FriendStatus.Solved -> "solved"
        FriendStatus.TakenDown -> "taken_down"
        FriendStatus.NeedsUpdate -> "needs_update"
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
        "Crown of the castle, a long title that wraps past two lines", "Mia", FriendStatus.Solved(228_000),
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

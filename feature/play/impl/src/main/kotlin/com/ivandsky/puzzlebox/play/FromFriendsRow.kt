package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
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
 * From friends at the top of the library (09 handoff, Variant A): the section head with its
 * count, a carousel of [cards] that snaps to its start, then the "Dailies" head over the
 * games. Hidden while there are no cards; the first one opens the section with a short
 * expand. A card opens its puzzle's card through [onOpen].
 */
@Composable
internal fun FromFriendsRow(cards: List<FriendCardModel>, onOpen: (ShareCode) -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    // The cards last shown, so the section's collapse still draws them.
    var last by remember { mutableStateOf(cards) }
    SideEffect { if (cards.isNotEmpty()) last = cards }
    val shown = cards.ifEmpty { last }
    AnimatedVisibility(
        visible = cards.isNotEmpty(),
        enter = if (reduced) {
            fadeIn(tween(MotionTokens.ReducedFadeDuration))
        } else {
            fadeIn(tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)) +
                expandVertically(tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
        },
        exit = if (reduced) {
            fadeOut(tween(MotionTokens.ReducedFadeDuration))
        } else {
            fadeOut(tween(MotionTokens.DurationShort)) + shrinkVertically(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard))
        },
        modifier = modifier.testTag("library.friends"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionHead("From friends", count = shown.size)
            val listState = rememberLazyListState()
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth().testTag("library.friends.carousel"),
            ) {
                items(shown, key = { it.row.code.value }) { card ->
                    FriendCard(
                        card = card,
                        reduced = reduced,
                        onClick = { onOpen(card.row.code) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = if (reduced) null else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
                            placementSpec = if (reduced) null else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
                            fadeOutSpec = if (reduced) null else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
                        ),
                    )
                }
            }
            SectionHead("Dailies", count = null, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun SectionHead(title: String, count: Int?, modifier: Modifier = Modifier) {
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

/** One friend's puzzle: game glyph and name, its title over "by <author>", and its status chip; the whole card is the target. */
@Composable
private fun FriendCard(card: FriendCardModel, reduced: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val code = card.row.code.value
    Surface(
        onClick = onClick,
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.width(CardWidth).testTag("library.friends.row.$code"),
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
        FriendStatus.TakenDown -> ChipLook(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
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

private fun previewCard(code: String, game: String, accent: AccentSlot, title: String, author: String, status: FriendStatus) =
    FriendCardModel(
        row = FriendPuzzleRow(checkNotNull(ShareCode.parse(code)), GameId(game.lowercase()), title, author, status),
        gameName = game,
        brand = GameBrand.Neutral.copy(accent = accent),
    )

private val PreviewCards = listOf(
    previewCard("K7Q2MX", "Tango", AccentSlot.Orange, "Sunday sun and moons", "Jonas", FriendStatus.InProgress(70_000)),
    previewCard("B4R9TZ", "Queens", AccentSlot.Violet, "Crown of the castle, a long title that wraps past two lines", "Mia", FriendStatus.Solved(228_000)),
    previewCard("M3N8PQ", "Zip", AccentSlot.Teal, "One line", "Ari", FriendStatus.TakenDown),
    previewCard("H2J5WX", "Queens", AccentSlot.Violet, "Fresh one", "Bo", FriendStatus.New),
    previewCard("Z9Y8XW", "Gone", AccentSlot.Neutral, "From a newer build", "Lee", FriendStatus.NeedsUpdate),
)

@PuzzleBoxPreviews
@Composable
internal fun FromFriendsRowPreview() {
    PuzzleBoxTheme {
        Surface {
            FromFriendsRow(PreviewCards, onOpen = {}, modifier = Modifier.padding(Spacing.lg))
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun FriendCardStatesPreview() {
    PuzzleBoxTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PreviewCards.forEach { FriendCard(it, reduced = true, onClick = {}) }
            }
        }
    }
}

package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * From friends at the top of the library (09 handoff, Variant A): the section head with its
 * count, a carousel of [cards] that snaps to its start, then the "Dailies" head over the
 * games. Hidden while there are no cards; the first one opens the section with a short
 * expand. A card opens its puzzle's card through [onOpen]; its long-press menu removes the
 * puzzle through [onRemove].
 */
@Composable
internal fun FromFriendsRow(
    cards: List<FriendCardModel>,
    onOpen: (ShareCode) -> Unit,
    onRemove: (ShareCode) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        onRemove = { onRemove(card.row.code) },
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

/**
 * "Removed K7Q-2MX" with Undo ([onUndo]) while [removed] names a code. It stays up until
 * [removed] turns null, so its time on screen is the view model's Undo window.
 */
@Composable
internal fun FriendRemovedSnackbar(removed: ShareCode?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(removed) {
        val code = removed ?: return@LaunchedEffect
        val result = host.showSnackbar("Removed ${code.display}", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
    SnackbarHost(host, modifier) { data ->
        RemovedSnackbar(data.visuals.message, data.visuals.actionLabel.orEmpty(), onAction = data::performAction)
    }
}

@Composable
private fun RemovedSnackbar(message: String, action: String, onAction: () -> Unit) {
    Snackbar(
        modifier = Modifier.testTag("library.friends.removed"),
        action = {
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                modifier = Modifier.testTag("library.friends.removed.undo"),
            ) { Text(action) }
        },
    ) { Text(message) }
}

@PuzzleBoxPreviews
@Composable
internal fun FromFriendsRowPreview() {
    PuzzleBoxTheme {
        Surface {
            FromFriendsRow(FriendPreviewCards, onOpen = {}, onRemove = {}, modifier = Modifier.padding(Spacing.lg))
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun FriendRemovedSnackbarPreview() {
    PuzzleBoxTheme {
        Surface {
            Box(Modifier.padding(Spacing.lg)) { RemovedSnackbar("Removed K7Q-2MX", "Undo", onAction = {}) }
        }
    }
}

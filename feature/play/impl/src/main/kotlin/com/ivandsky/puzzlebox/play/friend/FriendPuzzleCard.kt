package com.ivandsky.puzzlebox.play.friend

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalMutedStatus
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular

/**
 * A friend's puzzle before it is played: the game's glyph, the title, "by" the author and
 * the note, the size and difficulty chips (difficulty only when the local re-check graded
 * it, "Taken down · still plays" once the author took it down, popping in on the stamp's
 * spring when the card's recheck finds it), the author's time and hints beside yours once solved, the code with Copy, the one
 * primary action, [onAction], and under it the [BoardPreview], whose sign-in is [onSignIn].
 * Sections rise in with the Enter stagger the first time the code's card is shown; under
 * reduced motion they only fade.
 */
@Composable
internal fun FriendPuzzleCard(card: FriendCard, onAction: () -> Unit, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val entrance = rememberCardEntrance(card.puzzle.code)
    val accent = card.brand.accent.colors()
    val puzzle = card.puzzle
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Enter(entrance, 0, reduced) { FriendCardHead(card, reduced) }
        Enter(entrance, 1, reduced) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = puzzle.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("friend.card.title"),
                )
                Byline(puzzle.authorName, accent.container, accent.onContainer)
            }
        }
        if (puzzle.note.isNotBlank()) Enter(entrance, 2, reduced) { Note(puzzle.note, accent.accent) }
        Enter(entrance, 3, reduced) { Chips(card, accent.container, accent.onContainer, reduced) }
        Enter(entrance, 4, reduced) { Stats(card) }
        Enter(entrance, 5, reduced) { Action(card.action, reduced, onAction) }
        Enter(entrance, 6, reduced) {
            BoardPreview(
                board = card.board,
                author = puzzle.authorPin(),
                yours = card.yours,
                postState = puzzle.postState,
                signIn = card.signIn,
                onSignIn = onSignIn,
                closed = card.takenDown,
            )
        }
    }
}

@Composable
private fun Byline(author: String, container: Color, onContainer: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.size(Avatar).background(container, CircleShape), contentAlignment = Alignment.Center) {
            Text(author.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = onContainer)
        }
        Text(
            text = "by $author",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("friend.card.author"),
        )
    }
}

@Composable
private fun Note(note: String, rule: Color) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small),
    ) {
        Box(Modifier.width(NoteRule).fillMaxHeight().background(rule, MaterialTheme.shapes.small))
        Text(
            text = note,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(Spacing.md).testTag("friend.card.note"),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(card: FriendCard, container: Color, onContainer: Color, reduced: Boolean) {
    val neutral = MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val muted = LocalMutedStatus.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Chip(card.gameName, container, onContainer, "friend.card.game")
        Chip(card.size, neutral, ink, "friend.card.size")
        card.puzzle.difficulty?.let { Chip(it.name, neutral, ink, "friend.card.difficulty") }
        AnimatedVisibility(
            visible = card.takenDown,
            enter = if (reduced) {
                fadeIn(tween(MotionTokens.ReducedFadeDuration))
            } else {
                fadeIn(tween(MotionTokens.StampFadeDuration)) +
                    scaleIn(spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness), TAKEN_DOWN_POP_FROM_SCALE)
            },
            exit = fadeOut(tween(if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.StampFadeDuration)),
            label = "friend.card.taken_down",
        ) {
            Chip("Taken down · still plays", muted.container, muted.content, "friend.card.taken_down")
        }
    }
}

@Composable
private fun Chip(text: String, container: Color, content: Color, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = content,
        modifier = Modifier.background(container, PillShape).padding(horizontal = Spacing.md, vertical = Spacing.xs).testTag(tag),
    )
}

@Composable
private fun Stats(card: FriendCard) {
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

@Composable
private fun Action(action: FriendCardAction, reduced: Boolean, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AnimatedContent(targetState = action, transitionSpec = { crossfade(reduced) }, label = "cta") { shown ->
            Box(Modifier.testTag("friend.card.cta.${shown.tag}")) {
                PrimaryButton(text = shown.label, onClick = onAction, modifier = Modifier.fillMaxWidth().testTag("friend.card.play"))
            }
        }
        Text(
            text = "No sign-in needed to play.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** The card's swap between two contents: a short fade, instant under [reduced] motion. */
internal fun crossfade(reduced: Boolean) =
    fadeIn(tween(if (reduced) 0 else MotionTokens.SelectDuration)) togetherWith fadeOut(tween(if (reduced) 0 else MotionTokens.SelectDuration))

private val FriendCardAction.label: String
    get() = when (this) {
        FriendCardAction.Play -> "Play"
        is FriendCardAction.Resume -> "Resume · ${formatDuration(elapsedMs)}"
        FriendCardAction.Replay -> "Replay"
    }

private val FriendCardAction.tag: String
    get() = when (this) {
        FriendCardAction.Play -> "play"
        is FriendCardAction.Resume -> "resume"
        FriendCardAction.Replay -> "replay"
    }

private fun hintsLabel(hints: Int) = if (hints == 1) "1 hint" else "$hints hints"

// The taken-down chip pops in from a little smaller, as the From friends status chip does.
private const val TAKEN_DOWN_POP_FROM_SCALE = 0.85f

private val Avatar = 28.dp
private val NoteRule = 4.dp

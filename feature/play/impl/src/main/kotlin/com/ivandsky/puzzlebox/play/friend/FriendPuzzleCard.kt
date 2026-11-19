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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.ivandsky.puzzlebox.designsystem.LocalMutedStatus
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.TwoColumnMinWidth
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.maxContentWidth
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoardSection
import com.ivandsky.puzzlebox.resultui.DifficultyLabel
import com.ivandsky.puzzlebox.resultui.PlayAgainCopy

/**
 * A friend's puzzle before it is played: the game's glyph, the title, "by" the author and
 * the note, with [FriendCard.givens] the puzzle's board between them, blurred under "Hidden until
 * you play" until you have solved it ([HiddenBoardSection]), the size and difficulty chips (difficulty only when the local re-check graded
 * it, "Taken down · still plays" once the author took it down, popping in on the stamp's
 * spring when the card's recheck finds it), the author's time and hints beside yours once solved, the code with Copy, the one
 * primary action, [onAction], and under it the [BoardPreview], whose sign-in is [onSignIn]. A ⋮ beside the title offers
 * Report ([onReport]).
 * Sections rise in with the Enter stagger the first time the code's card is shown; under
 * reduced motion they only fade. On a wide window the card keeps to one centred column, unless it is in the Community
 * tablet's right pane, [paneHeight] high, with the board on and room for both columns: then [FriendPuzzleGrid] lays out
 * two, centred in the pane. A card in a pane carries no bottom margin in its [modifier]: the single column adds its own,
 * and the grid keeps its margins inside the pane's height.
 */
@Composable
internal fun FriendPuzzleCard(
    card: FriendCard,
    onAction: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    onReport: () -> Unit = {},
    paneHeight: Dp = Dp.Unspecified,
) {
    val reduced = rememberReducedMotion()
    val entrance = rememberCardEntrance(card.puzzle.code)
    if (paneHeight.isSpecified && card.hiddenBoard) {
        BoxWithConstraints(modifier) {
            if (maxWidth >= TwoColumnMinWidth) {
                FriendPuzzleGrid(card, entrance, reduced, paneHeight, onAction, onSignIn, onReport)
            } else {
                SingleColumn(card, entrance, reduced, onAction, onSignIn, onReport, Modifier.padding(bottom = Spacing.xl))
            }
        }
    } else {
        SingleColumn(card, entrance, reduced, onAction, onSignIn, onReport, modifier)
    }
}

@Composable
private fun SingleColumn(
    card: FriendCard,
    entrance: CardEntrance,
    reduced: Boolean,
    onAction: () -> Unit,
    onSignIn: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier,
) {
    val accent = card.brand.accent.colors()
    val puzzle = card.puzzle
    Column(modifier.maxContentWidth().padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Enter(entrance, 0, reduced) { FriendCardHead(card, reduced) }
        Enter(entrance, 1, reduced) { FriendTitleRow(card, onReport) }
        if (puzzle.note.isNotBlank()) Enter(entrance, 2, reduced) { Note(puzzle.note, accent.accent) }
        // The hidden board takes the slot after the note, so the sections after it stagger one later.
        val later = if (card.hiddenBoard) 1 else 0
        card.givens?.let { givens -> Enter(entrance, 3, reduced) { HiddenBoardSection(givens, sharp = card.yours != null, tagPrefix = "friend.card.board") } }
        Enter(entrance, 3 + later, reduced) { Chips(card, accent.container, accent.onContainer, reduced) }
        Enter(entrance, 4 + later, reduced) { Stats(card) }
        Enter(entrance, 5 + later, reduced) { Action(card.action, reduced, onAction) }
        Enter(entrance, 6 + later, reduced) {
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

/** The title and "by author" beside the ⋮ menu (Report). */
@Composable
internal fun FriendTitleRow(card: FriendCard, onReport: () -> Unit) {
    val accent = card.brand.accent.colors()
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = card.puzzle.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("friend.card.title"),
            )
            Byline(card.puzzle.authorName, accent.container, accent.onContainer)
        }
        FriendCardMenu(onReport)
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
internal fun Note(note: String, rule: Color) {
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
internal fun Chips(card: FriendCard, container: Color, onContainer: Color, reduced: Boolean) {
    val neutral = MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val muted = LocalMutedStatus.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Chip(card.gameName, container, onContainer, "friend.card.game")
        Chip(card.size, neutral, ink, "friend.card.size")
        card.puzzle.difficulty?.let {
            DifficultyLabel(it, MaterialTheme.typography.labelLarge, ink, Modifier.pill(neutral).testTag("friend.card.difficulty"))
        }
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
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = content, modifier = Modifier.pill(container).testTag(tag))
}

private fun Modifier.pill(container: Color) =
    background(container, PillShape).padding(horizontal = Spacing.md, vertical = Spacing.xs)

@Composable
internal fun Action(action: FriendCardAction, reduced: Boolean, onAction: () -> Unit) {
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
        FriendCardAction.PlayAgain -> PlayAgainCopy.BUTTON
    }

private val FriendCardAction.tag: String
    get() = when (this) {
        FriendCardAction.Play -> "play"
        is FriendCardAction.Resume -> "resume"
        FriendCardAction.PlayAgain -> "replay"
    }

// The taken-down chip pops in from a little smaller, as the From friends status chip does.
private const val TAKEN_DOWN_POP_FROM_SCALE = 0.85f

private val Avatar = 28.dp
private val NoteRule = 4.dp

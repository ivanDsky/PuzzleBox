package com.ivandsky.puzzlebox.play.friend

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A friend's puzzle before it is played: the game's glyph, the title, "by" the author and
 * the note, the size and difficulty chips (difficulty only when the local re-check graded
 * it), the author's time and hints beside yours once solved, the code with Copy, and the
 * one primary action, [onAction]. Sections rise in with the Enter stagger; under reduced
 * motion they only fade.
 */
@Composable
internal fun FriendPuzzleCard(card: FriendCard, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val accent = card.brand.accent.colors()
    val puzzle = card.puzzle
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Enter(0, reduced) { Head(card, reduced) }
        Enter(1, reduced) {
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
        if (puzzle.note.isNotBlank()) Enter(2, reduced) { Note(puzzle.note, accent.accent) }
        Enter(3, reduced) { Chips(card, accent.container, accent.onContainer) }
        Enter(4, reduced) { Stats(card) }
        Enter(5, reduced) { Action(card.action, reduced, onAction) }
    }
}

@Composable
private fun Head(card: FriendCard, reduced: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GameGlyph(card.brand, GameGlyphSize.Tile, Modifier.testTag("friend.card.glyph"))
        Text(
            text = "${card.gameName} · from a friend",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        CodePill(card.puzzle.code.display, reduced)
    }
}

@Composable
private fun CodePill(code: String, reduced: Boolean) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_HOLD_MS)
            copied = false
        }
    }
    Surface(
        onClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, code))) }
            copied = true
        },
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.heightIn(min = MinTouch).testTag("friend.card.copy"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("friend.card.code"),
            )
            AnimatedContent(targetState = copied, transitionSpec = { crossfade(reduced) }, label = "copy") { done ->
                if (done) {
                    Icon(PuzzleIcons.Check, contentDescription = "Copied", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                } else {
                    Text("Copy", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
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
private fun Chips(card: FriendCard, container: Color, onContainer: Color) {
    val neutral = MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Chip(card.gameName, container, onContainer, "friend.card.game")
        Chip(card.size, neutral, ink, "friend.card.size")
        card.puzzle.difficulty?.let { Chip(it.name, neutral, ink, "friend.card.difficulty") }
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
            Text(
                text = hintsLabel(card.puzzle.authorHints),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("friend.card.author.hints"),
            )
        }
        card.yourTimeMs?.let { Tile("Your time", Modifier.weight(1f)) { Value(formatDuration(it), "friend.card.solved") } }
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

/** One Enter step: rises [MotionTokens.EnterRise] and fades in after [index] staggers; reduced, it only fades. */
@Composable
private fun Enter(index: Int, reduced: Boolean, content: @Composable () -> Unit) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduced) {
            shown.animateTo(1f, tween(MotionTokens.ReducedFadeDuration))
        } else {
            delay(index.coerceAtMost(MotionTokens.EnterStaggerCap) * MotionTokens.EnterStagger.toLong())
            shown.animateTo(1f, tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
        }
    }
    val rise = with(LocalDensity.current) { MotionTokens.EnterRise.toPx() }
    Box(
        Modifier.graphicsLayer {
            alpha = shown.value
            translationY = if (reduced) 0f else (1f - shown.value) * rise
        },
    ) { content() }
}

private fun crossfade(reduced: Boolean) =
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

private val MinTouch = 48.dp
private val Avatar = 28.dp
private val NoteRule = 4.dp
private const val COPIED_HOLD_MS = 1_500L
private const val CLIP_LABEL = "PuzzleBox code"

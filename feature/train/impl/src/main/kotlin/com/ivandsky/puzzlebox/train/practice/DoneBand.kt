package com.ivandsky.puzzlebox.train.practice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlin.math.ceil

/** The height of the done band's drain bar along its bottom edge. */
private val DrainHeight = 5.dp

/**
 * The band of a finished board (prototype 03, variant A): solid primary, a check, "Board N of M done" and what comes
 * next: a countdown to the next board (or, on the chapter's last board, to its result) with a button that skips the
 * wait, or with [BoardHold.waitsForTap] (a screen reader is on) the tap that moves on. A bar along the bottom edge
 * drains over the hold unless motion is reduced; the count stays. [narrow] puts the count and the button on a row under
 * the title, so the band keeps the status band's height and the board never moves. Only the title is a live region,
 * so "Board N of M done" is read out once and the ticking line is not.
 */
@Composable
internal fun DoneBand(
    chapter: PracticeChapter,
    hold: BoardHold?,
    narrow: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    needsOtherTechnique: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val last = chapter.board == chapter.boards
    val shown = hold ?: BoardHold(BoardEndHoldMillis, waitsForTap = false)
    val shape = MaterialTheme.shapes.medium.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Box(modifier.fillMaxSize().clip(shape).background(scheme.primary).semantics(mergeDescendants = true) {}.testTag("train.status")) {
        val padding = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        if (narrow) {
            // Within the band's fixed height: the title with a small check, then the countdown beside the button.
            Column(padding.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    DoneCheck(SmallCheck)
                    DoneTitle(chapter)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    DoneNext(shown, last, needsOtherTechnique, Modifier.weight(1f))
                    NextBoardButton(last, onSkip)
                }
            }
        } else {
            Row(padding.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                DoneCheck(LargeCheck)
                Column(Modifier.weight(1f)) {
                    DoneTitle(chapter)
                    DoneNext(shown, last, needsOtherTechnique)
                }
                NextBoardButton(last, onSkip)
            }
        }
        if (!rememberReducedMotion() && !shown.waitsForTap) {
            val left by animateFloatAsState(shown.remainingMs / BoardEndHoldMillis.toFloat(), tween(DrainTickMillis, easing = LinearEasing), label = "boardEndDrain")
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(left).height(DrainHeight).background(scheme.onPrimary.copy(alpha = 0.45f)))
        }
    }
}

/** The drain bar eases over one hold tick, so it reads as one steady drain. */
private const val DrainTickMillis = 100

private val LargeCheck = 40.dp
private val SmallCheck = 26.dp

@Composable
private fun DoneCheck(size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary), contentAlignment = Alignment.Center) {
        Icon(PuzzleIcons.Check, contentDescription = null, Modifier.size(size * 0.6f), tint = MaterialTheme.colorScheme.primary)
    }
}

/** "Board N of M done": the band's live region. */
@Composable
private fun DoneTitle(chapter: PracticeChapter) {
    Text(
        "Board ${chapter.board} of ${chapter.boards} done",
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** What comes next: the countdown, or with [BoardHold.waitsForTap] the tap that moves on; [needsOtherTechnique] first says why an unsolved board stopped. */
@Composable
private fun DoneNext(hold: BoardHold, last: Boolean, needsOtherTechnique: Boolean, modifier: Modifier = Modifier) {
    val next = if (last) "Result" else "Next board"
    // The whole second left, never below 1.
    val seconds = ceil(hold.remainingMs / 1000.0).toInt().coerceAtLeast(1)
    val whenNext = if (hold.waitsForTap) "Tap ${if (last) "See result" else "Next board"} when you are ready." else "$next in $seconds"
    Text(
        if (needsOtherTechnique) "The next move needs another technique. $whenNext" else whenNext,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The skip: "Next board", or "See result" on the chapter's last board; a pill that is the on-primary fill with primary text. */
@Composable
private fun NextBoardButton(last: Boolean, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onSkip,
        modifier = modifier.testTag("train.nextBoard"),
        shape = PillShape,
        color = MaterialTheme.colorScheme.onPrimary,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(start = Spacing.lg, end = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (last) "See result" else "Next board", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            Icon(PuzzleIcons.Chevron, contentDescription = null)
        }
    }
}

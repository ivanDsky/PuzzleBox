package com.ivandsky.puzzlebox.train.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private val StepHeight = 48.dp
private val DotSize = 24.dp
private const val FadeMillis = 150

/**
 * The frame the intro opens a demo on: the evidence. Frame 0, the plain position, is skipped (user-check round 5: the
 * board already shows it), though it stays the position board 1 opens at.
 */
internal const val EvidenceFrame = 1

/** The first landing frame: every frame from here on is one step, since all the squares land for one reason. */
private const val LandingFrame = 2

/** The step [frame] of a demo belongs to: 0 for the evidence, 1 for every landing frame as one. */
internal fun stepOf(frame: Int): Int = if (frame < LandingFrame) 0 else 1

/** The caption of [frame]'s step out of a demo's [captions]: through the landings, the first landing's, which gives the reason. */
internal fun stepCaption(captions: List<String>, frame: Int): String = captions[frame.coerceIn(EvidenceFrame, LandingFrame)]

/**
 * The step track of a demo with [captions] (one per frame): two steps (the evidence, the squares landing) with the one
 * [frame] belongs to lifted and the one before it outlined. Tapping a step shows its frame ([onSeek]); the landing step
 * shows its end, every square landed. [compact] (the phone) is a row of numbered cells with the caption for a screen
 * reader; otherwise each step is a row with its number and its caption.
 */
@Composable
internal fun StepTrack(captions: List<String>, frame: Int, compact: Boolean, onSeek: (Int) -> Unit, modifier: Modifier = Modifier) {
    val steps = captions.subList(EvidenceFrame, minOf(LandingFrame + 1, captions.size))
    val current = stepOf(frame)
    val seek = { step: Int -> onSeek(if (step == 0) EvidenceFrame else captions.lastIndex) }
    if (compact) {
        Row(modifier.fillMaxWidth().testTag("train.watch.steps"), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            steps.indices.forEach { index -> Step(index, steps[index], current, compact = true, seek, Modifier.weight(1f)) }
        }
    } else {
        Column(modifier.fillMaxWidth().testTag("train.watch.steps"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            steps.indices.forEach { index -> Step(index, steps[index], current, compact = false, seek, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun Step(index: Int, caption: String, current: Int, compact: Boolean, onSeek: (Int) -> Unit, modifier: Modifier) {
    val now = index == current
    val spec = if (rememberReducedMotion()) snap() else tween<Color>(FadeMillis)
    val fill by animateColorAsState(if (now) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent, spec, label = "step")
    Row(
        modifier
            .heightIn(min = StepHeight)
            .clip(MaterialTheme.shapes.small)
            .background(fill)
            .clickable { onSeek(index) }
            .semantics(mergeDescendants = true) {
                selected = now
                if (compact) contentDescription = "Step ${index + 1}: $caption"
            }
            .padding(horizontal = Spacing.sm)
            .testTag("train.watch.step.$index"),
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(index + 1, passed = index < current, now = now)
        if (!compact) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A step's number in a disc: filled for the one shown, outlined for the ones before it, bare for the ones to come. */
@Composable
private fun Dot(number: Int, passed: Boolean, now: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val base = Modifier.size(DotSize).clip(CircleShape)
    val look = when {
        now -> base.background(scheme.primary)
        passed -> base.border(1.dp, scheme.primary, CircleShape)
        else -> base.border(1.dp, scheme.outlineVariant, CircleShape)
    }
    Box(look, contentAlignment = Alignment.Center) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (now) scheme.onPrimary else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(0.dp),
        )
    }
}

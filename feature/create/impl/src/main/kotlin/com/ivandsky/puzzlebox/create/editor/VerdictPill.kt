package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict

private val PillHeight = 36.dp
private val PillTouchHeight = 48.dp
private val PillMaxWidth = 168.dp

/**
 * The Studio top bar's verdict at a glance: one line in the verdict's tone. Its state
 * description is the verdict's machine name ("Unique", "Incomplete", …) for tests and
 * accessibility services alike. Motion (spec S19): a new verdict crossfades in on
 * MotionTokens short (already the 120 ms reduced-motion fade); entering Checking pulses once
 * (none under reduced motion), however many edits it stays Checking through; and settling on
 * Unique from another settled verdict fires exactly one haptic.
 */
@Composable
fun VerdictPill(verdict: Verdict, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val pulse = remember { Animatable(1f) }
    val checking = verdict == Verdict.Checking
    LaunchedEffect(checking) {
        if (checking && !reducedMotion) {
            val half = tween<Float>(MotionTokens.PulseDuration / 2, easing = MotionTokens.EasingStandard)
            pulse.animateTo(MotionTokens.PulseScale, half)
            pulse.animateTo(1f, half)
        } else {
            pulse.snapTo(1f)
        }
    }
    UniqueHaptic(verdict)
    Box(
        modifier = modifier
            .heightIn(min = PillTouchHeight)
            .testTag("create.editor.pill")
            .semantics { stateDescription = stateOf(verdict) },
        contentAlignment = Alignment.Center,
    ) {
        // A crossfade keyed on the verdict's kind: only a change of kind fades, not a new solution.
        AnimatedContent(
            targetState = verdict,
            modifier = Modifier.graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            },
            transitionSpec = {
                val fade = tween<Float>(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard)
                fadeIn(fade) togetherWith fadeOut(fade)
            },
            contentAlignment = Alignment.Center,
            label = "create-verdict-pill",
            contentKey = ::stateOf,
        ) { shown -> PillFace(shown) }
    }
}

@Composable
private fun PillFace(verdict: Verdict) {
    val (container, content) = toneOf(verdict)
    Surface(
        modifier = Modifier.height(PillHeight).widthIn(max = PillMaxWidth),
        shape = PillShape,
        color = container,
        contentColor = content,
    ) {
        Box(Modifier.padding(horizontal = Spacing.md), contentAlignment = Alignment.Center) {
            Text(
                text = labelOf(verdict),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One Confirm haptic when the settled verdict turns Unique; Checking in between and the first verdict on open don't count. */
@Composable
private fun UniqueHaptic(verdict: Verdict) {
    val haptics = LocalHapticFeedback.current
    val lastSettled = remember { mutableStateOf<Verdict?>(null) }
    LaunchedEffect(verdict) {
        if (verdict == Verdict.Checking) return@LaunchedEffect
        val before = lastSettled.value
        lastSettled.value = verdict
        if (verdict is Verdict.Unique && before != null && before !is Verdict.Unique) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
}

private fun stateOf(verdict: Verdict): String = when (verdict) {
    is Verdict.Incomplete -> "Incomplete"
    Verdict.Checking -> "Checking"
    is Verdict.Unique -> "Unique"
    is Verdict.Multiple -> "Multiple"
    is Verdict.None -> "None"
    Verdict.Undetermined -> "Undetermined"
}

private fun labelOf(verdict: Verdict): String = when (verdict) {
    is Verdict.Incomplete -> "Not checked"
    Verdict.Checking -> "Checking…"
    is Verdict.Unique -> "Unique"
    is Verdict.Multiple -> "2+ solutions"
    is Verdict.None -> "No solution"
    Verdict.Undetermined -> "Couldn't decide"
}

@Composable
private fun toneOf(verdict: Verdict): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (verdict) {
        is Verdict.Unique -> scheme.primaryContainer to scheme.onPrimaryContainer
        is Verdict.Multiple -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        is Verdict.None -> scheme.errorContainer to scheme.onErrorContainer
        is Verdict.Incomplete, Verdict.Checking, Verdict.Undetermined ->
            scheme.surfaceContainerHighest to scheme.onSurface
    }
}

@PuzzleBoxPreviews
@Composable
private fun VerdictPillPreview() {
    PuzzleBoxTheme {
        Surface {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                VerdictPill(Verdict.Incomplete(IncompleteReason("queens.unpainted", 16)))
                VerdictPill(Verdict.Checking)
                VerdictPill(Verdict.Unique(Solution(listOf(1, 7, 8, 14))))
                VerdictPill(Verdict.Multiple(Solution(emptyList()), Solution(emptyList()), emptySet()))
                VerdictPill(Verdict.None(emptySet()))
                VerdictPill(Verdict.Undetermined)
            }
        }
    }
}

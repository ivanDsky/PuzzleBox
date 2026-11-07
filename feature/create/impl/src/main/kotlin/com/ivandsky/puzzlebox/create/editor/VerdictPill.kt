package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.create.ui.colors
import com.ivandsky.puzzlebox.create.ui.glanceKey
import com.ivandsky.puzzlebox.create.ui.glanceLabel
import com.ivandsky.puzzlebox.create.ui.gradeState
import com.ivandsky.puzzlebox.create.ui.stateName
import com.ivandsky.puzzlebox.create.ui.tone
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.LadderRung
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict

private val PillHeight = 36.dp
private val PillTouchHeight = 48.dp
// Wide enough for "Unique · needs guessing" (spec A8) in labelLarge; the handoff's 168 dp clipped it.
private val PillMaxWidth = 208.dp

/**
 * The Studio top bar's verdict at a glance: one line in the verdict's tone; a Unique adds its
 * grade ("Unique · Medium", "Unique · needs guessing") under `create.editor.pill.grade`, whose
 * state is the grade's machine name. Tapping it ([onClick]) opens the verdict card. Its state
 * description is the verdict's machine name ("Unique", "Incomplete", …) for tests and
 * accessibility services alike. Motion (spec S19): a new verdict crossfades in on
 * MotionTokens short (already the 120 ms reduced-motion fade); entering Checking pulses once
 * (none under reduced motion), however many edits it stays Checking through; settling on
 * Unique from another settled verdict fires exactly one haptic; a press is Press feedback at
 * MotionTokens.PressScale.
 */
@Composable
fun VerdictPill(verdict: Verdict, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = PillTouchHeight)
            .pressScale(interactionSource, reducedMotion)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .testTag("create.editor.pill")
            .semantics { stateDescription = verdict.stateName() },
        contentAlignment = Alignment.Center,
    ) {
        // A crossfade keyed on the verdict's kind and grade: a new solution alone doesn't fade.
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
            contentKey = Verdict::glanceKey,
        ) { shown -> PillFace(shown) }
    }
}

@Composable
private fun PillFace(verdict: Verdict) {
    val (container, content) = verdict.tone().colors()
    Surface(
        modifier = Modifier.height(PillHeight).widthIn(max = PillMaxWidth),
        shape = PillShape,
        color = container,
        contentColor = content,
    ) {
        Box(Modifier.padding(horizontal = Spacing.md), contentAlignment = Alignment.Center) {
            val grade = (verdict as? Verdict.Unique)?.gradeState()
            Text(
                text = verdict.glanceLabel(),
                modifier = if (grade == null) {
                    Modifier
                } else {
                    Modifier.testTag("create.editor.pill.grade").semantics { stateDescription = grade }
                },
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

@PuzzleBoxPreviews
@Composable
private fun VerdictPillPreview() {
    PuzzleBoxTheme {
        Surface {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                VerdictPill(Verdict.Incomplete(IncompleteReason("queens.unpainted", 16)), onClick = {})
                VerdictPill(Verdict.Checking, onClick = {})
                VerdictPill(Verdict.Unique(Solution(listOf(1, 7, 8, 14))), onClick = {})
                VerdictPill(Verdict.Unique(Solution(listOf(1, 7, 8, 14)), Difficulty(PillPreviewRung)), onClick = {})
                VerdictPill(Verdict.Unique(Solution(listOf(1, 7, 8, 14)), needsGuessing = true), onClick = {})
                VerdictPill(Verdict.Multiple(Solution(emptyList()), Solution(emptyList()), emptySet()), onClick = {})
                VerdictPill(Verdict.None(emptySet()), onClick = {})
                VerdictPill(Verdict.Undetermined, onClick = {})
            }
        }
    }
}

private object PillPreviewRung : LadderRung {
    override val id: String = "preview.lineLock"
    override val level: Difficulty.Level = Difficulty.Level.Medium
    override val captionKey: String = "preview.lineLock"
}

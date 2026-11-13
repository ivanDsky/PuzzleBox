package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.beatDelayMs
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

// Index on the blocks beat: the streak row and the week dots take 0 and 1, this follows them.
private const val MOMENT_BEAT_INDEX = 2

// Enters from near full size, never from nothing: a scale-in this small reads as settling, not popping.
private const val MOMENT_FROM_SCALE = 0.95f

/**
 * The line saying a streak freeze was used or earned by this solve. Fades in with a scale-in
 * from [MOMENT_FROM_SCALE] on the Result blocks beat, after the streak count-up has run; no haptic.
 * Reduced motion: one plain fade, no scale, no delay. Previews show the end state.
 */
@Composable
internal fun FreezeMoment(text: String, modifier: Modifier = Modifier) {
    val settled = LocalInspectionMode.current
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(progress, reduced) {
        val spec = if (reduced) {
            tween<Float>(MotionTokens.ReducedFadeDuration)
        } else {
            tween(
                durationMillis = MotionTokens.EnterDuration,
                delayMillis = beatDelayMs(ResultBeats.Blocks, MOMENT_BEAT_INDEX),
                easing = MotionTokens.EasingEmphasizedDecelerate,
            )
        }
        progress.animateTo(1f, spec)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            if (!reduced) {
                val scale = MOMENT_FROM_SCALE + (1f - MOMENT_FROM_SCALE) * progress.value
                scaleX = scale
                scaleY = scale
            }
        },
    )
}

package com.ivandsky.puzzlebox.train.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlinx.coroutines.delay

private const val RiseMillis = 200
private val RiseDistance = 8.dp
private const val PopStart = 0.6f
private const val PopPeak = 1.12f
private const val PopPeakAt = 0.6f
private val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Rise: fades in while moving [RiseDistance] up, after [delayMillis]. Not at all under reduced motion. */
@Composable
internal fun Modifier.rise(delayMillis: Int = 0): Modifier {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    if (!reduced) {
        LaunchedEffect(Unit) {
            delay(delayMillis.toLong())
            progress.animateTo(1f, tween(RiseMillis, easing = Ease))
        }
    }
    val distance = with(LocalDensity.current) { RiseDistance.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * distance
    }
}

/** Pop-in: scales from 60% up past 100% and settles, over [durationMillis]. Not at all under reduced motion. */
@Composable
internal fun Modifier.pop(durationMillis: Int, enabled: Boolean = true): Modifier {
    val reduced = rememberReducedMotion()
    val still = reduced || !enabled
    val progress = remember { Animatable(if (still) 1f else 0f) }
    if (!still) LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis, easing = EaseOut)) }
    return graphicsLayer {
        val p = progress.value
        val scale = if (p < PopPeakAt) PopStart + (PopPeak - PopStart) * (p / PopPeakAt) else PopPeak - (PopPeak - 1f) * ((p - PopPeakAt) / (1f - PopPeakAt))
        scaleX = scale
        scaleY = scale
        alpha = minOf(1f, p * 3f)
    }
}

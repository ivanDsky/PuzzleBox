package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The old page's alpha at the end of its exit: a hair under 1 because AnimatedContent drops a page the moment its exit
 * has nothing left to animate, and a 1 -> 1 fade has none, so a true hold would vanish on the first frame.
 */
private const val HoldAlpha = 0.98f

/**
 * The tablet's right-pane switch: when [target] changes (by [contentKey]) the old page fades out and the new one in over
 * [MotionTokens.TabletPageCrossfade], in place: no slide, no rise, no scale, and the pane keeps its size. The old page
 * stays opaque under the fading new one, so two see-through pages never dip to the background halfway. Reduced motion
 * uses the app's [MotionTokens.ReducedFadeDuration] fade. Each page sits on [container] so one with see-through areas
 * never shows the page under it.
 */
@Composable
fun <T> HoldCrossfade(
    target: T,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.background,
    contentKey: (T) -> Any? = { it },
    label: String = "hold-crossfade",
    content: @Composable (T) -> Unit,
) {
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = target,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val millis = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.TabletPageCrossfade
            val fade = tween<Float>(millis, easing = MotionTokens.EasingStandard)
            (fadeIn(fade) togetherWith fadeOut(tween(millis), targetAlpha = HoldAlpha)).apply { targetContentZIndex = 1f }.using(null)
        },
        contentKey = contentKey,
        label = label,
    ) { page ->
        Box(Modifier.fillMaxSize().background(container)) { content(page) }
    }
}

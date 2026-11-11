package com.ivandsky.puzzlebox.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * First-composition entrance (handoff 05-b): a fade-only stagger, [MotionTokens.EnterStagger]
 * apart up to the cap, by list [index]. Reduced motion: one plain fade, no stagger. Applied
 * as a modifier so a section that may emit nothing (the summary rows) can enter without
 * leaving an empty child behind.
 */
@Composable
internal fun Modifier.staggerIn(index: Int): Modifier {
    val reducedMotion = rememberReducedMotion()
    // A preview has no first frame to animate: it draws every item settled.
    val settled = LocalInspectionMode.current
    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) delay(MotionTokens.EnterStagger.toLong() * index.coerceAtMost(MotionTokens.EnterStaggerCap))
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared || settled) 1f else 0f,
        animationSpec = if (reducedMotion) {
            tween(MotionTokens.ReducedFadeDuration)
        } else {
            tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)
        },
        label = "home-entrance-$index",
    )
    return graphicsLayer { alpha = progress }
}

/** [content] entering as list item [index] ([staggerIn]). */
@Composable
internal fun StaggeredItem(index: Int, content: @Composable () -> Unit) {
    Box(Modifier.staggerIn(index)) { content() }
}

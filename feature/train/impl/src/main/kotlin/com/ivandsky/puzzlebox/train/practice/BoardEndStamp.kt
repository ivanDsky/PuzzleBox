package com.ivandsky.puzzlebox.train.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private val StampSize = 112.dp
private val CheckSize = 64.dp
private const val RippleMillis = 700
private const val RippleToScale = 1.7f
private val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * The check a finished board is stamped with, centered on the board (prototype 03, variant A): a [StampSize] primary
 * disc with an on-primary check that lands on the stamp spring, and a ring that ripples out of it once. It takes no
 * touches and says nothing to a screen reader (the Done band does). Reduced motion shows the disc at once, with no ripple.
 */
@Composable
internal fun BoardEndStamp(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    var landed by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { landed = true }
    val ripple = remember { Animatable(0f) }
    if (!reduced) LaunchedEffect(Unit) { ripple.animateTo(1f, tween(RippleMillis, easing = EaseOutStrong)) }
    Box(modifier.size(StampSize).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (!reduced && ripple.value < 1f) {
            Box(
                Modifier
                    .size(StampSize)
                    .graphicsLayer {
                        val scale = 1f + (RippleToScale - 1f) * ripple.value
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.9f * (1f - ripple.value)
                    }
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Stamp(visible = landed) {
            Box(
                Modifier.size(StampSize).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(CheckSize), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

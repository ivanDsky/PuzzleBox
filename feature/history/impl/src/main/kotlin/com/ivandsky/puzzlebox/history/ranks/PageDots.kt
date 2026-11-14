package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private val DOT = 6.dp
private val DOT_SELECTED = 18.dp

/**
 * Where the swipe stands: one dot per board, the board at [index] drawn as a longer, accent one, which swells
 * to [pulseScale] (read while drawing) for the one-time nudge. A step stretches the new dot and shrinks the old
 * over [MotionTokens.DurationShort]; reduced motion keeps the colour's fade and drops the stretch. Nothing to
 * show for a single board.
 */
@Composable
internal fun PageDots(count: Int, index: Int, modifier: Modifier = Modifier, pulseScale: () -> Float = { 1f }) {
    if (count <= 1) return
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Board ${index + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { Dot(selected = it == index, pulseScale) }
    }
}

@Composable
private fun Dot(selected: Boolean, pulseScale: () -> Float) {
    val reduced = rememberReducedMotion()
    val width = animateDpAsState(
        if (selected) DOT_SELECTED else DOT,
        if (reduced) snap() else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
        label = "dotWidth",
    )
    val color = animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
        label = "dotColor",
    )
    Box(
        Modifier
            .width(width.value)
            .height(DOT)
            .graphicsLayer {
                if (selected) {
                    scaleX = pulseScale()
                    scaleY = scaleX
                }
            }
            .clip(CircleShape)
            .background(color.value),
    )
}

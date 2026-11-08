package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion

private val BadgeMinSize = 26.dp
private val BadgeRing = 2.dp

/**
 * How many hints are banked, as a dark pill on the Hint button's corner. A new number pops it
 * ([StrandsMotion.BadgePopFrom] to [StrandsMotion.BadgePopPeak] to 1, eased out); [reduceMotion]
 * and its first composition just show it. The button's own state description says the same in words.
 */
@Composable
internal fun HintBadge(count: Int, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(1f) }
    val shown = remember { Counted(count) }
    LaunchedEffect(count, reduceMotion) {
        if (shown.count == count) return@LaunchedEffect
        shown.count = count
        if (reduceMotion) {
            pop.snapTo(1f)
            return@LaunchedEffect
        }
        pop.snapTo(0f)
        pop.animateTo(1f, tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate))
    }
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .testTag("strands.hint_badge")
            .graphicsLayer {
                val progress = pop.value
                val scale = if (progress < PeakAt) {
                    StrandsMotion.BadgePopFrom + (StrandsMotion.BadgePopPeak - StrandsMotion.BadgePopFrom) * progress / PeakAt
                } else {
                    StrandsMotion.BadgePopPeak - (StrandsMotion.BadgePopPeak - 1f) * (progress - PeakAt) / (1f - PeakAt)
                }
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(BadgeMinSize, BadgeMinSize)
            .background(MaterialTheme.colorScheme.onSurface, CircleShape)
            .border(BadgeRing, surface, CircleShape)
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge.merge(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
            color = { surface },
        )
    }
}

/** Where in the pop the number is at its largest. */
private const val PeakAt = 0.6f

/** The last number shown, so a recomposition that changes nothing does not pop. Not state: nothing reads it. */
private class Counted(var count: Int)

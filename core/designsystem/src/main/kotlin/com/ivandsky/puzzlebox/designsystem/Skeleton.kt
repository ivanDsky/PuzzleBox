package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private const val SHEEN_ALPHA = 0.6f

/** The sheen band's width as a share of the skeleton's own width. */
private const val SHEEN_WIDTH = 0.5f

/** Tag on a skeleton whose sheen is running; a static (reduced-motion) skeleton lacks it. */
const val SHIMMER_TAG = "card.skeleton.shimmer"

/**
 * A loading placeholder fill: [color] in [shape] with a sheen sweeping start→end in a linear
 * [MotionTokens.ShimmerDuration] loop. Every loading placeholder goes through this, so they all look and move alike.
 * Reduced motion (and previews) draw it static.
 */
@Composable
fun Modifier.skeleton(color: Color, shape: Shape): Modifier {
    val base = clip(shape).background(color)
    if (rememberReducedMotion() || LocalInspectionMode.current) return base
    val progress = rememberSheenProgress()
    val sheen = MaterialTheme.colorScheme.background.copy(alpha = SHEEN_ALPHA)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return base.testTag(SHIMMER_TAG).drawWithContent {
        drawContent()
        val band = size.width * SHEEN_WIDTH
        val left = sheenLeft(progress.value, size.width, rtl)
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, sheen, Color.Transparent),
                startX = left,
                endX = left + band,
            ),
            topLeft = Offset(left, 0f),
            size = size.copy(width = band),
        )
    }
}

/**
 * The sheen band's left edge at [progress] (0..1) across a skeleton [width] px wide. The band travels from fully before the
 * start edge to fully past the end edge, so at 0.5 its centre is the skeleton's centre and the travel before and after
 * it is equal; [rtl] mirrors it, starting at the right edge.
 */
internal fun sheenLeft(progress: Float, width: Float, rtl: Boolean): Float {
    val band = width * SHEEN_WIDTH
    val travelled = -band + (width + band) * progress
    return if (rtl) width - travelled - band else travelled
}

@Composable
private fun rememberSheenProgress(): State<Float> = rememberInfiniteTransition(label = "skeleton-shimmer").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(MotionTokens.ShimmerDuration, easing = LinearEasing), RepeatMode.Restart),
    label = "skeleton-sheen",
)

// Preview: a line and a block, static as every preview draws a skeleton.
@PuzzleBoxPreviews
@Composable
internal fun SkeletonPreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val fill = MaterialTheme.colorScheme.surfaceContainerHigh
            Box(Modifier.fillMaxWidth(0.6f).height(16.dp).skeleton(fill, MaterialTheme.shapes.small))
            Box(Modifier.fillMaxWidth().height(96.dp).skeleton(fill, MaterialTheme.shapes.medium))
        }
    }
}

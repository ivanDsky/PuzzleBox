package com.ivandsky.puzzlebox.gameui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalOnFill
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/** Skeleton fill: the slot's foreground at 16% (hub-card §Tokens). */
internal const val SKELETON_ALPHA = 0.16f
private const val SHEEN_ALPHA = 0.6f

/** The sheen band's width as a share of the skeleton's own width. */
private const val SHEEN_WIDTH = 0.5f

/** Tag on a skeleton whose sheen is running; a static (reduced-motion) skeleton lacks it. */
internal const val SHIMMER_TAG = "card.skeleton.shimmer"

/**
 * A loading placeholder for one text line: a [color] pill [widthFraction] of the slot
 * wide and [height] tall, with a sheen sweeping start→end in a linear [MotionTokens.ShimmerDuration]
 * loop. Reduced motion (and previews) draw it static.
 */
@Composable
fun SkeletonLine(widthFraction: Float, height: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(widthFraction).height(height).skeleton(color, PillShape))
}

/** The loading status mark: a [size] skeleton circle. */
@Composable
internal fun SkeletonCircle(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size).skeleton(color, CircleShape))
}

@Composable
private fun Modifier.skeleton(color: Color, shape: Shape): Modifier {
    val base = clip(shape).background(color)
    if (rememberReducedMotion() || LocalInspectionMode.current) return base
    val progress = rememberSheenProgress()
    val sheen = MaterialTheme.colorScheme.background.copy(alpha = SHEEN_ALPHA)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return base.testTag(SHIMMER_TAG).drawWithContent {
        drawContent()
        val band = size.width * SHEEN_WIDTH
        // Travels from fully before the start edge to fully past the end edge.
        val travelled = -band + (size.width + band) * progress.value
        val left = if (rtl) size.width - travelled - band else travelled
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

@Composable
private fun rememberSheenProgress(): State<Float> = rememberInfiniteTransition(label = "skeleton-shimmer").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(MotionTokens.ShimmerDuration, easing = LinearEasing), RepeatMode.Restart),
    label = "skeleton-sheen",
)

// Preview: the loading panel's four line widths (30/70/55/40%) and a mark circle.
@PuzzleBoxPreviews
@Composable
internal fun SkeletonLinePreview() {
    PuzzleBoxTheme {
        val color = LocalOnFill.current.copy(alpha = SKELETON_ALPHA)
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.onSurface).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(0.3f, 0.7f, 0.55f, 0.4f).forEach { SkeletonLine(widthFraction = it, height = 12.dp, color = color) }
            SkeletonCircle(size = 16.dp, color = color)
        }
    }
}

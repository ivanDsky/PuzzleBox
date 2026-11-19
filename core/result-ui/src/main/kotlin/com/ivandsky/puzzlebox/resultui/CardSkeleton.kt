package com.ivandsky.puzzlebox.resultui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalOnFill
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.skeleton

/** Skeleton fill: the slot's foreground at 16% (hub-card §Tokens). */
internal const val SKELETON_ALPHA = 0.16f
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

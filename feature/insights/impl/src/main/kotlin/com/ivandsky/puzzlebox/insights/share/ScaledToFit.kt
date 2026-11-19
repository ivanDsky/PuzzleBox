package com.ivandsky.puzzlebox.insights.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shows the share image on screen at the largest size that fits, keeping its proportions. The image is measured at
 * its real [ShareImageWidthPx] by [ShareImageHeightPx] and only drawn smaller, so the picture on screen is the one
 * that gets shared, not a re-layout of it.
 */
@Composable
internal fun ScaledToFit(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val image = measurables.single().measure(Constraints.fixed(ShareImageWidthPx, ShareImageHeightPx))
        val scale = min(constraints.maxWidth / ShareImageWidthPx.toFloat(), constraints.maxHeight / ShareImageHeightPx.toFloat())
        layout((ShareImageWidthPx * scale).roundToInt(), (ShareImageHeightPx * scale).roundToInt()) {
            image.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

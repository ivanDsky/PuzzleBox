package com.ivandsky.puzzlebox.designsystem.chart

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.abs

/** How many pixels of [image] are [color] (within a small tolerance, so antialiased edges do not count as it). */
internal fun countPixels(image: ImageBitmap, color: Color): Int {
    val map = image.toPixelMap()
    var count = 0
    for (y in 0 until map.height) for (x in 0 until map.width) {
        val p = map[x, y]
        if (abs(p.red - color.red) < 0.02f && abs(p.green - color.green) < 0.02f && abs(p.blue - color.blue) < 0.02f) count++
    }
    return count
}

/** How many pixels of [image] differ from the [background] it was drawn on: all the ink, text and rules included. */
internal fun countInk(image: ImageBitmap, background: Color): Int {
    val map = image.toPixelMap()
    var count = 0
    for (y in 0 until map.height) for (x in 0 until map.width) {
        val p = map[x, y]
        if (abs(p.red - background.red) > 0.02f || abs(p.green - background.green) > 0.02f || abs(p.blue - background.blue) > 0.02f) count++
    }
    return count
}

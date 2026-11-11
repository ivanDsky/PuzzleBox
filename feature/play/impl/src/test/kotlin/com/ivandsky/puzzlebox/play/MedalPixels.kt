package com.ivandsky.puzzlebox.play

import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.abs
import androidx.compose.ui.graphics.Color as ComposeColor

/** Whether the place cell [this] draws a disc of the [medal]'s colour anywhere; a plain number draws none. */
internal fun SemanticsNodeInteraction.showsMedal(medal: ComposeColor): Boolean {
    val image = captureToImage().asAndroidBitmap()
    val want = medal.toArgb()
    for (x in 0 until image.width) for (y in 0 until image.height) {
        val pixel = image.getPixel(x, y)
        if (abs(Color.red(pixel) - Color.red(want)) <= 2 &&
            abs(Color.green(pixel) - Color.green(want)) <= 2 &&
            abs(Color.blue(pixel) - Color.blue(want)) <= 2
        ) return true
    }
    return false
}

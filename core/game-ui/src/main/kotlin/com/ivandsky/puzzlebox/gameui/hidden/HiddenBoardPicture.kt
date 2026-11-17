package com.ivandsky.puzzlebox.gameui.hidden

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The blurred board as a picture, for a draw that cannot blur a layer. The content is drawn into a bitmap many
 * times smaller than the board, blurred there on the calling thread ([HiddenBoardBoxBlur]), and stretched back
 * over the board's bounds. It runs inside the draw pass, so there is no frame in which the content shows sharp.
 * The bitmap and its pixel buffer are kept between draws of the same size.
 */
internal class HiddenBoardPicture {
    private var bitmap: ImageBitmap? = null
    private var pixels = IntArray(0)

    fun ContentDrawScope.drawBlurred(sigmaPx: Float, wash: ColorFilter) {
        if (size.width < 1f || size.height < 1f) return
        val shrink = HiddenBoardBoxBlur.shrinkFor(sigmaPx)
        val width = ceil(size.width / shrink).toInt()
        val height = ceil(size.height / shrink).toInt()
        val target = bitmap?.takeIf { it.width == width && it.height == height }
            ?: ImageBitmap(width, height).also {
                bitmap = it
                pixels = IntArray(width * height)
            }
        val android = target.asAndroidBitmap()
        android.eraseColor(0)

        val canvas = Canvas(target)
        canvas.save()
        canvas.scale(width / size.width, height / size.height)
        // The content draws into the bitmap, not onto the screen.
        draw(this, layoutDirection, canvas, size) { this@drawBlurred.drawContent() }
        canvas.restore()

        android.getPixels(pixels, 0, width, 0, 0, width, height)
        HiddenBoardBoxBlur.blur(pixels, width, height, HiddenBoardBoxBlur.radiusFor(sigmaPx / shrink))
        android.setPixels(pixels, 0, width, 0, 0, width, height)

        drawImage(
            image = target,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            colorFilter = wash,
            filterQuality = FilterQuality.Low,
        )
    }
}

package com.ivandsky.puzzlebox.gameui.hidden

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The blur of the path below Android 12, where a layer cannot blur itself: three passes of a box blur
 * over a small picture of the board, which together are close to a Gaussian. Edges repeat the edge pixel.
 */
internal object HiddenBoardBoxBlur {
    const val PASSES: Int = 3

    /** The picture is drawn this many times smaller than the board, so its blur stays about this wide in pixels. */
    private const val SHRUNK_SIGMA_PX = 4f

    /** How much smaller than the board the picture is drawn, for a blur of [sigmaPx] at full size. */
    fun shrinkFor(sigmaPx: Float): Int = (sigmaPx / SHRUNK_SIGMA_PX).toInt().coerceAtLeast(1)

    /** The box radius whose [PASSES] passes spread like a Gaussian of standard deviation [sigma]. */
    fun radiusFor(sigma: Float): Int {
        val width = sqrt(12f * sigma * sigma / PASSES + 1f)
        return ((width - 1f) / 2f).roundToInt().coerceAtLeast(1)
    }

    /** Blurs [pixels] (ARGB, not premultiplied, row-major, [width] by [height]) in place. */
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0 || width <= 0 || height <= 0) return
        premultiply(pixels)
        val scratch = IntArray(pixels.size)
        repeat(PASSES) {
            for (y in 0 until height) line(pixels, scratch, start = y * width, step = 1, length = width, radius = radius)
            for (x in 0 until width) line(scratch, pixels, start = x, step = width, length = height, radius = radius)
        }
        unpremultiply(pixels)
    }

    /** One box pass along one row or column: a running sum of the window, moved one pixel at a time. */
    private fun line(src: IntArray, dst: IntArray, start: Int, step: Int, length: Int, radius: Int) {
        val window = 2 * radius + 1
        val half = window / 2
        val last = length - 1
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val p = src[start + step * i.coerceIn(0, last)]
            a += p ushr 24
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        for (i in 0 until length) {
            dst[start + step * i] = (((a + half) / window) shl 24) or (((r + half) / window) shl 16) or
                (((g + half) / window) shl 8) or ((b + half) / window)
            val leaving = src[start + step * (i - radius).coerceAtLeast(0)]
            val entering = src[start + step * (i + radius + 1).coerceAtMost(last)]
            a += (entering ushr 24) - (leaving ushr 24)
            r += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            g += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            b += (entering and 0xFF) - (leaving and 0xFF)
        }
    }

    // Colour is weighted by coverage while it is averaged, so a clear pixel never darkens its neighbours.
    private fun premultiply(pixels: IntArray) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr 24
            if (a == 0xFF) continue
            val r = (((p shr 16) and 0xFF) * a + 127) / 255
            val g = (((p shr 8) and 0xFF) * a + 127) / 255
            val b = ((p and 0xFF) * a + 127) / 255
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun unpremultiply(pixels: IntArray) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr 24
            if (a == 0xFF) continue
            if (a == 0) {
                pixels[i] = 0
                continue
            }
            val r = (((p shr 16) and 0xFF) * 255 / a).coerceAtMost(255)
            val g = (((p shr 8) and 0xFF) * 255 / a).coerceAtMost(255)
            val b = ((p and 0xFF) * 255 / a).coerceAtMost(255)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

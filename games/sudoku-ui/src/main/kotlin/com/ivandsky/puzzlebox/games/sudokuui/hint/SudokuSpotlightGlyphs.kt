package com.ivandsky.puzzlebox.games.sudokuui.hint

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole

/**
 * The digit text a Spotlight draws over the grid. [texts] maps a cell to its text: the digit for a target or
 * culprit, the struck digits for an excluded cell. The overlay owns colour, alpha and sequence; this only draws text.
 *
 * The platform canvas draws it because [draw] is a plain DrawScope extension with no TextMeasurer. The digit is
 * centred on the cell and sized at 0.6 of it, like the grid's own digit; a pixel of drift against the grid's Text
 * is possible, and the baseline here is the only place to adjust it.
 */
internal data class SudokuSpotlightGlyphs(val texts: Map<Int, String>) : SpotlightGlyphs {

    // Drawing runs on one thread; the paint is reused and fully reset by every call. Not part of equality.
    private val paint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            fontFeatureSettings = "tnum"
        }
    }

    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
        val text = texts[cell] ?: return
        when (role) {
            SpotlightRole.TARGET -> drawDigits(text, size, color, DIGIT_SCALE, heavy = false, struck = false)
            SpotlightRole.CULPRIT -> drawDigits(text, size, color, DIGIT_SCALE, heavy = true, struck = false)
            // One struck digit reads big, several read small, like the prototype's 3x2 candidates.
            SpotlightRole.EXCLUDED ->
                drawDigits(text, size, color, if (text.length == 1) DIGIT_SCALE else CANDIDATES_SCALE, heavy = false, struck = true)
            SpotlightRole.EVIDENCE -> Unit
        }
    }

    private fun DrawScope.drawDigits(text: String, size: Size, color: Color, scale: Float, heavy: Boolean, struck: Boolean) {
        paint.color = color.toArgb()
        paint.textSize = size.minDimension * scale
        paint.typeface = if (heavy) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val centre = Offset(size.width / 2, size.height / 2)
        val metrics = paint.fontMetrics
        drawContext.canvas.nativeCanvas.drawText(text, centre.x, centre.y - (metrics.ascent + metrics.descent) / 2, paint)
        if (struck) {
            val half = paint.measureText(text) / 2
            drawLine(color, Offset(centre.x - half, centre.y), Offset(centre.x + half, centre.y), strokeWidth = paint.textSize * STRIKE_WIDTH)
        }
    }

    private companion object {
        const val DIGIT_SCALE = 0.6f
        const val CANDIDATES_SCALE = 0.3f
        const val STRIKE_WIDTH = 0.08f
    }
}

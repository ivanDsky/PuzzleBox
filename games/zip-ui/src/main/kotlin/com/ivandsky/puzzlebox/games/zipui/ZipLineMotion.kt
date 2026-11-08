package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.DragTraceHooks
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.TraceLineMotion
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope

/**
 * The Zip line's frame state over the shared [TraceLineMotion]: Zip draws the line, its lip
 * and the head on the visible end. [finger] (board px, read in the draw phase) leans the end
 * at a cell [hooks] accepts while the drag runs. Called from the draw phase only: the finger
 * and the Animatables are read there, so a moving finger or a running tween redraws without
 * recomposing the board.
 */
internal class ZipLineMotion(scope: CoroutineScope) {
    private val motion = TraceLineMotion<GridCell>(scope)
    private val stroke = Path()

    fun DrawScope.drawTrace(
        path: List<GridCell>,
        finger: Offset?,
        hooks: DragTraceHooks?,
        n: Int,
        color: Color,
        lip: Color,
        widthFraction: Float,
        lipFraction: Float,
        animate: Boolean,
    ) {
        val pitchX = size.width / n
        val pitchY = size.height / n
        val fingerCells = finger?.let { Offset(it.x / pitchX, it.y / pitchY) }
        val tip = if (fingerCells != null && hooks != null) zipTip(path, fingerCells, hooks) else null
        val end = motion.step(path, tip, animate)
        if (path.isEmpty()) return
        val line = motion.line
        fun centre(index: Int): Offset {
            val cell = line[index]
            return Offset((cell.col + 0.5f) * pitchX, (cell.row + 0.5f) * pitchY)
        }
        val width = minOf(pitchX, pitchY) * widthFraction
        val lastIndex = line.lastIndex
        val last = floor(end).toInt().coerceIn(0, lastIndex)
        val endPoint = if (last < lastIndex) {
            val from = centre(last)
            from + (centre(last + 1) - from) * (end - last)
        } else {
            centre(last)
        }
        val style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val headRadius = width * MotionTokens.Trace.HeadScale / 2
        if (end > 0f) {
            stroke.reset()
            val start = centre(0)
            stroke.moveTo(start.x, start.y)
            for (i in 1..last) centre(i).let { stroke.lineTo(it.x, it.y) }
            stroke.lineTo(endPoint.x, endPoint.y)
        }
        // The lip (spec A14): the same line and head, a fraction of the pitch lower, in the darker hue.
        translate(top = minOf(pitchX, pitchY) * lipFraction) {
            if (end > 0f) drawPath(stroke, lip, style = style)
            drawCircle(lip, radius = headRadius, center = endPoint)
        }
        if (end > 0f) drawPath(stroke, color, style = style)
        drawCircle(color, radius = headRadius, center = endPoint)
    }
}

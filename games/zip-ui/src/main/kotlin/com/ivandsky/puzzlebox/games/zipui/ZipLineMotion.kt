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
import com.ivandsky.puzzlebox.gameui.SnapLineMotion
import com.ivandsky.puzzlebox.gameui.TraceLineMotion
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope

/**
 * The Zip line's frame state (prototype 02 variant D) over the shared [TraceLineMotion]: Zip
 * projects its 4-way tip and draws the line, its lip and the head. Called from the draw phase
 * only: the finger and the Animatables are read there, so a moving finger or a running tween
 * redraws without recomposing the board.
 */
internal class ZipLineMotion(scope: CoroutineScope) {
    private val motion = TraceLineMotion<GridCell, TipAxis>(scope) { cell, axis -> GridCell(cell.row + axis.dRow, cell.col + axis.dCol) }
    private var latch: TipAxis? = null
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
        val tip = if (fingerCells != null && hooks != null) traceTip(path, fingerCells, n, n, hooks, latch) else null
        if (fingerCells == null) latch = null else if (tip != null) latch = tip.direction
        val end = motion.step(path, tip, animate)
        if (path.isEmpty()) return
        val tipCell = motion.tipCell
        fun centre(index: Int): Offset {
            val cell = if (index < path.size) path[index] else checkNotNull(tipCell)
            return Offset((cell.col + 0.5f) * pitchX, (cell.row + 0.5f) * pitchY)
        }
        val width = minOf(pitchX, pitchY) * widthFraction
        val lastIndex = path.size - if (tipCell == null) 1 else 0
        val last = floor(end).toInt().coerceIn(0, lastIndex)
        val endPoint = if (last < lastIndex) {
            val from = centre(last)
            from + (centre(last + 1) - from) * (end - last)
        } else {
            centre(last)
        }
        val style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val headRadius = width * MotionTokens.Trace.HeadScale / 2 * motion.headScale
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

/**
 * The Zip line under the Snap flag over the shared [SnapLineMotion]: no finger, no tip, no
 * pop. The end sits on the last cell centre; the head disc stays at its resting size. Same
 * stroke, lip and head look as [ZipLineMotion]. Called from the draw phase only.
 */
internal class ZipSnapLine(scope: CoroutineScope) {
    private val motion = SnapLineMotion<GridCell>(scope)
    private val stroke = Path()

    fun DrawScope.drawTrace(
        path: List<GridCell>,
        n: Int,
        color: Color,
        lip: Color,
        widthFraction: Float,
        lipFraction: Float,
        animate: Boolean,
    ) {
        val end = motion.step(path, animate)
        val line = motion.line
        if (line.isEmpty()) return
        val pitchX = size.width / n
        val pitchY = size.height / n
        fun centre(index: Int): Offset = line[index].let { Offset((it.col + 0.5f) * pitchX, (it.row + 0.5f) * pitchY) }
        val width = minOf(pitchX, pitchY) * widthFraction
        val last = floor(end).toInt().coerceIn(0, line.lastIndex)
        val endPoint = if (last < line.lastIndex) {
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
        translate(top = minOf(pitchX, pitchY) * lipFraction) {
            if (end > 0f) drawPath(stroke, lip, style = style)
            drawCircle(lip, radius = headRadius, center = endPoint)
        }
        if (end > 0f) drawPath(stroke, color, style = style)
        drawCircle(color, radius = headRadius, center = endPoint)
    }
}

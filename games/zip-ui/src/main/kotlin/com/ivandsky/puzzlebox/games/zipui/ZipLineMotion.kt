package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.DragTraceHooks
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.TraceLineMotion
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope

/**
 * The Zip line's frame state over the shared [TraceLineMotion]: Zip draws the line (one stroke, no lip) and the head on the visible end. While the drag runs, [finger] (board px, read in the draw
 * phase) puts the end where [ZipTipAim] projects it on a step [hooks] accepts, on the frame
 * of the pointer event: a held line is drawn without blends. Called from the draw phase
 * only: the finger and the Animatables are read there, so a moving finger or a running tween
 * redraws without recomposing the board.
 */
internal class ZipLineMotion(scope: CoroutineScope) {
    private val motion = TraceLineMotion<GridCell>(scope)
    private val aim = ZipTipAim()
    private val stroke = Path()

    fun DrawScope.drawTrace(
        path: List<GridCell>,
        finger: Offset?,
        hooks: DragTraceHooks?,
        n: Int,
        color: Color,
        widthFraction: Float,
        animate: Boolean,
    ) {
        val pitchX = size.width / n
        val pitchY = size.height / n
        val fingerCells = finger?.let { Offset(it.x / pitchX, it.y / pitchY) }
        val tip = hooks?.let { aim.tip(path, fingerCells, it) }
        val end = motion.step(path, tip, animate, held = finger != null)
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
        if (end > 0f) drawPath(stroke, color, style = style)
        drawCircle(color, radius = headRadius, center = endPoint)
    }
}

package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.TraceLineMotion
import com.ivandsky.puzzlebox.games.strands.Coord
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope

/**
 * The live trace connector's frame state over the shared [TraceLineMotion]: Strands draws the
 * connector and the head disc on the visible end. [finger] (cell units, read in the draw
 * phase) leans the end along one legal direction from the last letter while the drag runs.
 * Called from the draw phase only: the finger, the trace and the Animatables are read there,
 * so a moving finger or a running tween redraws the connector and recomposes nothing.
 */
internal class StrandsLineMotion(scope: CoroutineScope) {
    private val motion = TraceLineMotion<Coord>(scope)
    private val line = Path()

    fun DrawScope.drawTrace(
        path: List<Coord>,
        finger: Offset?,
        found: (cell: Coord) -> Boolean,
        rows: Int,
        cols: Int,
        color: Color,
        stroke: Stroke,
        animate: Boolean,
    ) {
        val tip = finger?.let { strandsTip(path, it, rows, cols, found) }
        val end = motion.step(path, tip, animate)
        if (path.isEmpty()) return
        val cells = motion.line
        val pitchX = size.width / cols
        val pitchY = size.height / rows
        fun centre(index: Int): Offset {
            val cell = cells[index]
            return Offset((cell.col + 0.5f) * pitchX, (cell.row + 0.5f) * pitchY)
        }
        val lastIndex = cells.lastIndex
        val last = floor(end).toInt().coerceIn(0, lastIndex)
        val endPoint = if (last < lastIndex) {
            val from = centre(last)
            from + (centre(last + 1) - from) * (end - last)
        } else {
            centre(last)
        }
        if (end > 0f) {
            line.reset()
            val start = centre(0)
            line.moveTo(start.x, start.y)
            for (i in 1..last) centre(i).let { line.lineTo(it.x, it.y) }
            line.lineTo(endPoint.x, endPoint.y)
            drawPath(line, color, style = stroke)
        }
        if (cells.size >= 2) {
            drawCircle(color, radius = stroke.width * MotionTokens.Trace.HeadScale / 2, center = endPoint)
        }
    }
}

package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The Zip line's frame state (prototype 02 variant D): the committed path is instant, the
 * VISIBLE end is a fractional index along it plus an optional tip cell, blended over
 * [MotionTokens.Trace] durations whenever the path or the tip direction changes. Called
 * from the draw phase only: the finger and the Animatables are read there, so a moving
 * finger or a running tween redraws without recomposing the board.
 */
internal class ZipLineMotion(private val scope: CoroutineScope) {
    private val blend = Animatable(1f)
    private val head = Animatable(1f)
    private var blendFrom = 0f
    private var blendDuration = 0
    private var blendEasing: Easing = LinearEasing

    // The tween's first frame is drawn before its coroutine snaps progress to 0.
    private var blendPending = false

    private var drawnPath: List<GridCell>? = null
    private var drawnLine: List<GridCell> = emptyList()
    private var drawnEnd = 0f
    private var tipCell: GridCell? = null
    private var latch: TipAxis? = null
    private var hadTip = false
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
        val (line, end) = step(path, finger?.let { Offset(it.x / pitchX, it.y / pitchY) }, hooks, n, animate)
        if (line.isEmpty()) return
        fun centre(cell: GridCell) = Offset((cell.col + 0.5f) * pitchX, (cell.row + 0.5f) * pitchY)
        val width = minOf(pitchX, pitchY) * widthFraction
        val last = floor(end).toInt().coerceIn(0, line.lastIndex)
        val endPoint = if (last < line.lastIndex) {
            val from = centre(line[last])
            from + (centre(line[last + 1]) - from) * (end - last)
        } else {
            centre(line[last])
        }
        if (end > 0f) {
            stroke.reset()
            val start = centre(line[0])
            stroke.moveTo(start.x, start.y)
            for (i in 1..last) centre(line[i]).let { stroke.lineTo(it.x, it.y) }
            stroke.lineTo(endPoint.x, endPoint.y)
            drawPath(stroke, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawCircle(color, radius = width * MotionTokens.Trace.HeadScale / 2 * head.value, center = endPoint)
    }

    /** The line to draw (path + tip cell) and how far along it the visible end sits. */
    private fun step(
        path: List<GridCell>,
        finger: Offset?,
        hooks: DragTraceHooks?,
        n: Int,
        animate: Boolean,
    ): Pair<List<GridCell>, Float> {
        val previous = drawnPath
        drawnPath = path
        if (path.isEmpty()) {
            drawnLine = emptyList()
            drawnEnd = 0f
            tipCell = null
            hadTip = false
            return path to 0f
        }
        val changed = previous != null && previous != path
        if (changed && animate && path.size > commonPrefix(previous, path)) pop()

        val tip = if (finger != null && hooks != null) traceTip(path, finger, n, n, hooks, latch) else null
        if (finger == null) latch = null else if (tip != null) latch = tip.axis
        var target = path.lastIndex.toFloat()
        if (tip != null) {
            if (tip.kind == TipKind.Back) {
                target -= tip.extent
            } else {
                val tail = path.last()
                tipCell = GridCell(tail.row + tip.axis.dRow, tail.col + tip.axis.dCol)
                target += tip.extent
            }
        }
        val line = tipCell?.let { path + it } ?: path

        val shared = commonPrefix(drawnLine, line)
        val mapped = if (shared > 0) minOf(drawnEnd, (shared - 1).toFloat()) else target
        val truncated = shared > 0 && mapped < drawnEnd - 1e-6f
        val releasing = hadTip && tip == null
        hadTip = tip != null
        val threshold = if (releasing) 0.01f else MotionTokens.Trace.BlendMinCells
        when {
            !animate -> Unit
            shared > 0 && (changed || truncated || releasing) && abs(target - mapped) > threshold -> {
                val grow = target > mapped
                startBlend(
                    from = mapped,
                    duration = when {
                        grow -> MotionTokens.Trace.GrowDuration
                        releasing -> MotionTokens.Trace.HomeDuration
                        else -> MotionTokens.Trace.RetractDuration
                    },
                    easing = if (grow) MotionTokens.Trace.EasingOut else MotionTokens.Trace.EasingRetract,
                )
            }
            blending() && (changed || truncated) -> startBlend(mapped, blendDuration, blendEasing)
        }
        val end = if (animate && blending()) {
            val progress = if (blendPending) 0f else blend.value
            blendFrom + (target - blendFrom) * blendEasing.transform(progress)
        } else {
            target
        }
        drawnLine = line
        drawnEnd = end
        return line to end
    }

    private fun blending() = blendPending || blend.value < 1f

    private fun startBlend(from: Float, duration: Int, easing: Easing) {
        blendFrom = from
        blendDuration = duration
        blendEasing = easing
        blendPending = true
        scope.launch {
            blend.snapTo(0f)
            blendPending = false
            blend.animateTo(1f, tween(duration, easing = LinearEasing))
        }
    }

    private fun pop() {
        val rise = (MotionTokens.Trace.PopDuration * MotionTokens.Trace.PopRiseFraction).toInt()
        scope.launch {
            head.snapTo(1f)
            head.animateTo(MotionTokens.Trace.PopScale, tween(rise, easing = MotionTokens.Trace.EasingOut))
            head.animateTo(1f, tween(MotionTokens.Trace.PopDuration - rise, easing = MotionTokens.EasingStandard))
        }
    }

    private fun commonPrefix(a: List<GridCell>, b: List<GridCell>): Int {
        var k = 0
        while (k < a.size && k < b.size && a[k] == b[k]) k++
        return k
    }
}

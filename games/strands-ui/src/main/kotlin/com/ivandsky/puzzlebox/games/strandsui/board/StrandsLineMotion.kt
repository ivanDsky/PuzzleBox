package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strands.Coord
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The live trace connector's frame state (prototype 02 variant E, Zip's ZipLineMotion
 * shape): the committed path is instant; the VISIBLE end is a fractional index along the
 * path plus an optional tip letter, blended over [MotionTokens.Trace] durations whenever
 * the path or the tip direction changes, with a head disc that pops on every letter joined.
 * Called from the draw phase only: the finger, the trace and the Animatables are read
 * there, so a moving finger or a running tween redraws the connector and recomposes nothing.
 */
internal class StrandsLineMotion(private val scope: CoroutineScope) {
    private val blend = Animatable(1f)
    private val head = Animatable(1f)
    private var blendFrom = 0f
    private var blendDuration = 0
    private var blendEasing: Easing = LinearEasing

    // The tween's first frame is drawn before its coroutine snaps progress to 0.
    private var blendPending = false

    private var drawnPath: List<Coord>? = null
    private var drawnLine: List<Coord> = emptyList()
    private var drawnTip: Coord? = null
    private var drawnEnd = 0f

    // The last letter the tip reached for; it stays on the line so the tip can retract home along it.
    private var tipCell: Coord? = null
    private var latch: TraceDirection? = null
    private var hadTip = false
    private val line = Path()

    fun DrawScope.drawTrace(
        path: List<Coord>,
        finger: TraceFinger?,
        rows: Int,
        cols: Int,
        color: Color,
        stroke: Stroke,
        animate: Boolean,
    ) {
        val end = step(path, finger, rows, cols, animate)
        if (path.isEmpty()) return
        val pitchX = size.width / cols
        val pitchY = size.height / rows
        fun centre(index: Int): Offset {
            val cell = if (index < path.size) path[index] else checkNotNull(tipCell)
            return Offset((cell.col + 0.5f) * pitchX, (cell.row + 0.5f) * pitchY)
        }
        val lastIndex = path.size - if (tipCell == null) 1 else 0
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
        if (path.size >= 2) {
            drawCircle(color, radius = stroke.width * MotionTokens.Trace.HeadScale / 2 * head.value, center = endPoint)
        }
    }

    /** How far along path + [tipCell] the visible end sits this frame. */
    private fun step(path: List<Coord>, finger: TraceFinger?, rows: Int, cols: Int, animate: Boolean): Float {
        val previous = drawnPath
        drawnPath = path
        if (path.isEmpty()) {
            drawnLine = path
            drawnTip = null
            drawnEnd = 0f
            tipCell = null
            latch = null
            hadTip = false
            return 0f
        }
        val changed = previous != null && previous !== path && previous != path
        if (changed && animate && path.size > commonPrefix(previous.orEmpty(), null, path, null)) pop()

        val tip = finger?.let { strandsTip(path, it, rows, cols, latch) }
        if (finger == null) latch = null else if (tip != null) latch = tip.direction
        var target = path.lastIndex.toFloat()
        if (tip != null) {
            if (tip.kind == TipKind.Back) {
                target -= tip.extent
            } else {
                val tail = path.last()
                tipCell = Coord(tail.row + tip.direction.dRow, tail.col + tip.direction.dCol)
                target += tip.extent
            }
        }

        val shared = commonPrefix(drawnLine, drawnTip, path, tipCell)
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
        drawnLine = path
        drawnTip = tipCell
        drawnEnd = end
        return end
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
}

/** The shared head of the lines [a] + [aTip] and [b] + [bTip], each tip optional; no list is built. */
private fun commonPrefix(a: List<Coord>, aTip: Coord?, b: List<Coord>, bTip: Coord?): Int {
    val aSize = a.size + if (aTip == null) 0 else 1
    val bSize = b.size + if (bTip == null) 0 else 1
    var k = 0
    while (k < aSize && k < bSize && (if (k < a.size) a[k] else aTip) == (if (k < b.size) b[k] else bTip)) k++
    return k
}

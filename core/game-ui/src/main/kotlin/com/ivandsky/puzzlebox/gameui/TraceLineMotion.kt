package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.tracePop
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A trace line's frame state (Zip and Strands, spec A1/A4): the committed path is instant; the
 * VISIBLE end is a fractional index along the path plus an optional [tipCell], blended over
 * [MotionTokens.Trace] durations whenever the path or the tip direction changes, with a head
 * that pops on every cell joined. Each game projects its tip and draws; [neighbour] is the cell
 * one step from a cell along a tip direction. Call [step] from the draw phase only, so a running
 * tween redraws the line and recomposes nothing.
 */
class TraceLineMotion<C : Any, D>(
    private val scope: CoroutineScope,
    private val neighbour: (cell: C, direction: D) -> C,
) {
    private val blend = Animatable(1f)
    private val head = Animatable(1f)
    private var blendFrom = 0f
    private var blendDuration = 0
    private var blendEasing: Easing = LinearEasing

    // The tween's first frame is drawn before its coroutine snaps progress to 0.
    private var blendPending = false

    private var drawnPath: List<C>? = null
    private var drawnLine: List<C> = emptyList()
    private var drawnTip: C? = null
    private var drawnEnd = 0f
    private var hadTip = false

    /** The last cell the tip reached for; it stays on the line so the tip can retract home along it. */
    var tipCell: C? = null
        private set

    /** The head disc's scale this frame: 1 at rest, above it mid-pop. */
    val headScale: Float get() = head.value

    /** How far along path + [tipCell] the visible end sits this frame. */
    fun step(path: List<C>, tip: TraceTip<D>?, animate: Boolean): Float {
        val previous = drawnPath
        drawnPath = path
        if (path.isEmpty()) {
            drawnLine = path
            drawnTip = null
            drawnEnd = 0f
            tipCell = null
            hadTip = false
            return 0f
        }
        val changed = previous != null && previous !== path && previous != path
        if (changed && animate && path.size > commonPrefix(previous.orEmpty(), null, path, null)) pop()

        var target = path.lastIndex.toFloat()
        if (tip != null) {
            if (tip.kind == TipKind.Back) {
                target -= tip.extent
            } else {
                tipCell = neighbour(path.last(), tip.direction)
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
        scope.launch {
            head.snapTo(1f)
            head.tracePop()
        }
    }
}

/** The shared head of the lines [a] + [aTip] and [b] + [bTip], each tip optional; no list is built. */
private fun <C> commonPrefix(a: List<C>, aTip: C?, b: List<C>, bTip: C?): Int {
    val aSize = a.size + if (aTip == null) 0 else 1
    val bSize = b.size + if (bTip == null) 0 else 1
    var k = 0
    while (k < aSize && k < bSize && (if (k < a.size) a[k] else aTip) == (if (k < b.size) b[k] else bTip)) k++
    return k
}

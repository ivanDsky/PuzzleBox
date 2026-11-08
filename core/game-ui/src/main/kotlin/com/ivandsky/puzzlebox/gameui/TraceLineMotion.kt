package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A trace line's frame state (Zip and Strands, spec A1): the committed path is instant; the
 * VISIBLE end is a fractional index along [line], blended over [MotionTokens.Trace]
 * durations whenever the path changes. The end never leaves the last committed cell by
 * itself: the head sits there and only the grow and retract blends move it between cells.
 * A backtrack keeps the removed cells in [line] until the end has retracted past them.
 * Each game draws [line] up to the end. Call [step] from the draw phase only, so a running
 * tween redraws the line and recomposes nothing.
 */
class TraceLineMotion<C : Any>(private val scope: CoroutineScope) {
    private val blend = Animatable(1f)
    private var blendFrom = 0f
    private var blendDuration = 0
    private var blendEasing: Easing = LinearEasing

    // The tween's first frame is drawn before its coroutine snaps progress to 0.
    private var blendPending = false

    private var drawnPath: List<C>? = null
    private var drawnEnd = 0f

    /** The cells to draw this frame: the path, plus the cells a backtrack is still retracting off. */
    var line: List<C> = emptyList()
        private set

    /** How far along [line] the visible end sits this frame. */
    fun step(path: List<C>, animate: Boolean): Float {
        val previous = drawnPath
        drawnPath = path
        if (path.isEmpty()) {
            line = path
            drawnEnd = 0f
            return 0f
        }
        val changed = previous != null && previous != path

        val target = path.lastIndex.toFloat()
        val shared = commonPrefix(line, path)
        val retracting = shared == path.size && line.size > path.size
        val mapped = if (shared > 0) minOf(drawnEnd, (shared - 1).toFloat()) else target
        val truncated = shared > 0 && mapped < drawnEnd - 1e-6f
        when {
            !animate -> line = path
            retracting -> when {
                // Off the old line along the removed cells; one short enough is not worth a blend.
                changed && drawnEnd - target > MotionTokens.Trace.BlendMinCells ->
                    startBlend(drawnEnd, MotionTokens.Trace.RetractDuration, MotionTokens.Trace.EasingRetract)
                changed -> line = path
                !blending() -> line = path
            }
            else -> {
                line = path
                when {
                    shared > 0 && (changed || truncated) && abs(target - mapped) > MotionTokens.Trace.BlendMinCells ->
                        startBlend(mapped, MotionTokens.Trace.GrowDuration, MotionTokens.Trace.EasingOut)
                    blending() && (changed || truncated) -> startBlend(mapped, blendDuration, blendEasing)
                }
            }
        }
        val end = if (animate && blending()) {
            val progress = if (blendPending) 0f else blend.value
            blendFrom + (target - blendFrom) * blendEasing.transform(progress)
        } else {
            target
        }
        drawnEnd = end
        return end
    }

    // Reads blend.value first, every call: the draw that starts a blend sees blendPending and
    // would otherwise read no state, so the tween's first snap and every frame after it would
    // never redraw the line.
    private fun blending() = blend.value < 1f || blendPending

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
}

/** How many leading cells [a] and [b] share. */
private fun <C> commonPrefix(a: List<C>, b: List<C>): Int {
    var k = 0
    while (k < a.size && k < b.size && a[k] == b[k]) k++
    return k
}

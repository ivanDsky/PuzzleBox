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
 * A trace line's frame state (Zip and Strands, spec A1/A4): the committed path is instant; the
 * VISIBLE end is a fractional index along [line], blended over [MotionTokens.Trace]
 * durations whenever the path changes. A live [TraceTip] ([TraceTip.extent] of a step past the
 * last committed cell, or back toward the previous one) moves the end with the finger without
 * a blend, resting on the tail whenever the tip is refused; let go, it returns home over
 * [MotionTokens.Trace.HomeDuration]. A committed backtrack keeps the removed cells in [line]
 * until the end has retracted past them. Each game projects its tip and draws [line] up to the
 * end. Call [step] from the draw phase only, so a running tween redraws the line and
 * recomposes nothing.
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
    private var hadTip = false

    /** The cells to draw this frame: the path and its tip cell, plus the cells a backtrack is still retracting off. */
    var line: List<C> = emptyList()
        private set

    /** How far along [line] the visible end sits this frame. */
    fun step(path: List<C>, tip: TraceTip<C>? = null, animate: Boolean): Float {
        val previous = drawnPath
        drawnPath = path
        if (path.isEmpty()) {
            line = path
            drawnEnd = 0f
            hadTip = false
            return 0f
        }
        val changed = previous != null && previous != path

        // A forward tip leans at a cell the game has not committed, so its cell rides the line's
        // end; a back tip rides the committed line, which already runs through the previous cell.
        val tipCell = tip?.takeIf { it.kind == TipKind.Forward }?.toward
        val target = path.lastIndex.toFloat() + when (tip?.kind) {
            TipKind.Forward -> tip.extent
            TipKind.Back -> -tip.extent
            null -> 0f
        }
        val releasing = hadTip && tip == null
        val arming = !hadTip && tip != null
        hadTip = tip != null

        val nextLine = path + listOfNotNull(tipCell)
        val shared = commonPrefix(line, nextLine)
        // A dropped tip cell is a release, not a leftover to retract along.
        val retracting = !releasing && shared == nextLine.size && line.size > nextLine.size
        val mapped = if (shared > 0) minOf(drawnEnd, (shared - 1).toFloat()) else target
        val truncated = shared > 0 && mapped < drawnEnd - 1e-6f
        when {
            !animate -> line = nextLine
            retracting -> when {
                // Off the old line along the removed cells; one short enough is not worth a blend.
                changed && drawnEnd - target > MotionTokens.Trace.BlendMinCells ->
                    startBlend(drawnEnd, MotionTokens.Trace.RetractDuration, MotionTokens.Trace.EasingRetract)
                changed -> line = nextLine
                !blending() -> line = nextLine
            }
            else -> {
                line = nextLine
                when {
                    // Let go mid-lean, or swung into a refused direction: home from wherever the
                    // end is drawn, however short the way back.
                    releasing && abs(target - drawnEnd) > 0.01f ->
                        startBlend(drawnEnd, MotionTokens.Trace.HomeDuration, MotionTokens.Trace.EasingRetract)
                    // A lean appearing far from the tail (a finger swung off a refused direction)
                    // grows in rather than snapping.
                    arming && abs(target - drawnEnd) > MotionTokens.Trace.BlendMinCells ->
                        startBlend(
                            drawnEnd,
                            if (target > drawnEnd) MotionTokens.Trace.GrowDuration else MotionTokens.Trace.RetractDuration,
                            if (target > drawnEnd) MotionTokens.Trace.EasingOut else MotionTokens.Trace.EasingRetract,
                        )
                    shared > 0 && (changed || truncated) && abs(target - mapped) > MotionTokens.Trace.BlendMinCells ->
                        startBlend(
                            mapped,
                            if (target > mapped) MotionTokens.Trace.GrowDuration else MotionTokens.Trace.RetractDuration,
                            if (target > mapped) MotionTokens.Trace.EasingOut else MotionTokens.Trace.EasingRetract,
                        )
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

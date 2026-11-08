package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The Snap trace line's frame state (Zip and Strands): the line ends on a committed cell
 * and nowhere else. There is no tip, no finger and no head pop, so the head disc is always
 * drawn at rest size.
 *
 * The visible end is a fractional index along [line]. A longer path grows it over
 * [MotionTokens.Trace.SnapGrowDuration], a shorter one retracts it over
 * [MotionTokens.Trace.SnapRetractDuration], both on [MotionTokens.Trace.EasingOut]. A change
 * under [MotionTokens.Trace.SnapBlendMinCells] is drawn at once. If the path changes while a
 * blend runs, the blend restarts from where the end is drawn now; if the new target is that
 * close already, the blend stops and the end jumps. [line] is the path itself
 * except while a backtrack retracts: then it is the longer path just left, so the end has a
 * line to run back along. Call [step] from the draw phase only, so a running tween redraws
 * the line and recomposes nothing.
 */
class SnapLineMotion<C : Any>(private val scope: CoroutineScope) {
    private val blend = Animatable(1f)
    private var blendFrom = 0f
    private var blendDuration = 0

    // The tween's first frame is drawn before its coroutine snaps progress to 0.
    private var blendPending = false

    // False after a clear, so a blend still running for the old line never bleeds into the next.
    private var live = false

    private var drawnPath: List<C>? = null
    private var drawnEnd = 0f

    /** The cells the end of the last [step] is indexed along; draw the polyline through them. */
    var line: List<C> = emptyList()
        private set

    /** How far along [line] the visible end sits this frame, in index units. */
    fun step(path: List<C>, animate: Boolean): Float {
        val previous = drawnPath
        drawnPath = path
        if (path.isEmpty()) {
            line = path
            drawnEnd = 0f
            if (blending()) stopBlend()
            return 0f
        }
        val changed = previous != null && previous !== path && previous != path

        val target = path.lastIndex.toFloat()
        val shared = commonPrefix(line, path)
        val retracting = shared == path.size && line.size > path.size
        val mapped = when {
            shared == 0 -> target
            retracting -> drawnEnd
            else -> minOf(drawnEnd, (shared - 1).toFloat())
        }
        when {
            !animate || !changed -> Unit
            shared > 0 && abs(target - mapped) > MotionTokens.Trace.SnapBlendMinCells ->
                startBlend(
                    from = mapped,
                    duration = if (target > mapped) {
                        MotionTokens.Trace.SnapGrowDuration
                    } else {
                        MotionTokens.Trace.SnapRetractDuration
                    },
                )
            blending() -> stopBlend()
        }
        val animating = animate && blending()
        val end = if (animating) {
            // Read even while pending: the draw must observe the tween, or nothing redraws it.
            val value = blend.value
            val progress = if (blendPending) 0f else value
            blendFrom + (target - blendFrom) * MotionTokens.Trace.EasingOut.transform(progress)
        } else {
            target
        }
        if (!(animating && retracting)) line = path
        drawnEnd = end
        return end
    }

    private fun blending() = live && (blendPending || blend.value < 1f)

    private fun stopBlend() {
        live = false
        blendPending = false
        scope.launch { blend.snapTo(1f) }
    }

    private fun startBlend(from: Float, duration: Int) {
        blendFrom = from
        blendDuration = duration
        live = true
        blendPending = true
        scope.launch {
            blend.snapTo(0f)
            blendPending = false
            blend.animateTo(1f, tween(duration, easing = LinearEasing))
        }
    }
}

private fun <C> commonPrefix(a: List<C>, b: List<C>): Int {
    var k = 0
    while (k < a.size && k < b.size && a[k] == b[k]) k++
    return k
}

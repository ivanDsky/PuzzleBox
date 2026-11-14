package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.SwipeMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The pull past the hub's first or last board: how far, in px, the finger has dragged beyond the end. The content
 * leans [lean] with it. A finger pulls it and takes it back ([dragBy]) and lets go ([release]), and it settles to
 * nothing on a spring in [scope]; a new drag picks the pull up wherever the settle has got to.
 */
@Stable
internal class EdgePull(private val scope: CoroutineScope) {
    private var pulled by mutableFloatStateOf(0f)
    private var settling: Job? = null

    /**
     * How far the content leans, px: [SwipeMotion.EdgeFollow] of the pull. It moves every frame of a settle, so it
     * is for the layout and draw phases only, never for composition.
     */
    val lean: Float get() = pulled * SwipeMotion.EdgeFollow

    /**
     * The finger moves by [dx] px (negative = leftwards). A move back inward is taken out of the pull first; [scrub]
     * is handed what is left of it for the boards, even when that is nothing (any drag takes hold of them), and
     * returns the part they did not take (not zero only past the first or last board), which pulls.
     */
    fun dragBy(dx: Float, scrub: (Float) -> Float) {
        settling?.cancel()
        val back = if (pulled * dx < 0f) dx.coerceIn(-abs(pulled), abs(pulled)) else 0f
        pulled += back + scrub(dx - back)
    }

    /** The finger lets go, or the touch is taken away: the pull settles back to nothing. */
    fun release() {
        settling = scope.launch {
            animate(pulled, 0f, animationSpec = SwipeMotion.Settle) { value, _ -> pulled = value }
        }
    }
}

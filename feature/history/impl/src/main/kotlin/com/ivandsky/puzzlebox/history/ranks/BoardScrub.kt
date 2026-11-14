package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/** A scrub this far along has arrived for anything that has to pick a side. */
private const val HALFWAY = 0.5f

/**
 * Where the hub stands between two of its boards: [from] and [to] are places in the hub's swipe order and [progress]
 * runs 0 to 1 from the one to the other. At rest they are the same board. [from] and [to] change only when a move
 * starts or lands; [progress] moves every frame, so it is for the layout and draw phases only (a `graphicsLayer`, a
 * `drawBehind`), never for composition.
 */
@Stable
internal class BoardScrub(position: Int) {
    var from by mutableIntStateOf(position)
        private set
    var to by mutableIntStateOf(position)
        private set
    private val travel = Animatable(0f)

    /** How far from [from] to [to], 0 to 1; 0 at rest. */
    val progress: Float get() = if (from == to) 0f else travel.value

    /** The colour between the two boards' own, each board's being its entry in [colors] ([fallback] for a board with none). */
    fun blend(colors: List<Color>, fallback: Color): Color =
        lerp(colors.getOrElse(from) { fallback }, colors.getOrElse(to) { fallback }, progress)

    /**
     * Moves to [position] and lands there. A move already on its way to another board starts over from the side it
     * is nearer to; one to [position] itself carries on. With [instant] (reduced motion) it lands at once.
     */
    suspend fun morphTo(position: Int, instant: Boolean) {
        if (position != to) {
            val arrived = travel.value >= HALFWAY
            travel.snapTo(0f)
            if (arrived) from = to
            to = position
        }
        if (from == to) return
        if (instant) travel.snapTo(1f) else travel.animateTo(1f, Settle)
        from = to
        travel.snapTo(0f)
    }
}

// Critically damped: the boards settle, they do not bounce.
private val Settle = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)

/** The hub's scrub, following [position]: a board picked anywhere (a swipe, the title menu, the tablet's list) morphs to it. */
@Composable
internal fun rememberBoardScrub(position: Int): BoardScrub {
    val scrub = remember { BoardScrub(position) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(position, reduced) { scrub.morphTo(position, reduced) }
    return scrub
}

package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** A scrub this far along has arrived for anything that has to pick a side. */
private const val HALFWAY = 0.5f

/**
 * Where the hub stands between two of its boards: [from] and [to] are places in the hub's swipe order and [progress]
 * runs 0 to 1 from the one to the other. At rest they are the same board. [from] and [to] change only when a move
 * starts, turns round or lands; [progress] moves every frame, so it is for the layout and draw phases only (a
 * `graphicsLayer`, a `drawBehind`), never for composition.
 *
 * Two things move it. A finger scrubs it ([dragBy]) and lets go ([release], or [cancel] when the touch is taken
 * away), and it settles on a spring to the board the release picked. A board picked any other way arrives through
 * [moveTo]. Either can interrupt the other at any point: the scrub carries on from where it is. The settling runs in
 * [scope].
 */
@Stable
internal class BoardScrub(position: Int, private val scope: CoroutineScope) {
    var from by mutableIntStateOf(position)
        private set
    var to by mutableIntStateOf(position)
        private set
    private var travel by mutableFloatStateOf(0f)

    // The board the hub is on, which is where the scrub ends up once nothing holds it.
    private var wanted = position
    private var held = false

    // Where the scrub was when the finger took it: a release decides by how far the finger has moved it since.
    private var grabbedAt = 0f
    private var grabbedTo = position
    private var arriving = false
    private var motion: Job? = null

    /** How far from [from] to [to], 0 to 1; 0 at rest. */
    val progress: Float get() = if (from == to) 0f else travel.coerceIn(0f, 1f)

    /** The colour between the two boards' own, each board's being its entry in [colors] ([fallback] for a board with none). */
    fun blend(colors: List<Color>, fallback: Color): Color =
        lerp(colors.getOrElse(from) { fallback }, colors.getOrElse(to) { fallback }, progress)

    /** How much of the hub is the board at [index] now: 1 at rest on it, [progress] of the way while it arrives. */
    fun share(index: Int): Float = (if (index == from) 1f - progress else 0f) + (if (index == to) progress else 0f)

    /**
     * The finger moves the scrub by [delta] board widths (negative = leftwards = towards the next board) among
     * [count] boards, taking it over from whatever moved it. It scrubs to the neighbour on either side of [from] and
     * no further; returns the part of [delta] it did not take, which is not zero only past the first or last board.
     */
    fun dragBy(delta: Float, count: Int): Float {
        if (!held) grab()
        val at = (if (to >= from) travel else -travel) - delta
        val reach = at.coerceIn(if (from > 0) -1f else 0f, if (from < count - 1) 1f else 0f)
        to = from + sign(reach).toInt()
        travel = abs(reach)
        return reach - at
    }

    /**
     * The finger lets go while the hub is on [position], moving at [velocity] board widths a second (positive =
     * towards the next board). Returns the board the scrub settles on, for the hub to go to: [to] for a fling its
     * way ([fling] or faster) or a drag of [commit] board widths, otherwise [from]. A swipe that had already
     * committed and is only caught on its way stays committed unless the finger takes it [commit] back.
     */
    fun release(position: Int, velocity: Float, fling: Float, commit: Float): Int {
        held = false
        if (to == from) {
            moveTo(position, instant = false)
            return position
        }
        val forward = to > from
        val arrive = when {
            abs(velocity) >= fling -> (velocity > 0f) == forward
            position == to && to == grabbedTo -> grabbedAt - travel < commit
            else -> travel >= commit
        }
        val target = if (arrive) to else from
        wanted = target
        settle(arrive, if (forward) velocity else -velocity)
        return target
    }

    /** The touch was taken away mid-scrub: settles on the board the hub is on. */
    fun cancel() {
        held = false
        settle(arrive = wanted == to && to != from, speed = 0f)
    }

    /**
     * Moves to [position] and lands there, unless a finger holds the scrub (it goes there on release). A move already
     * on its way to another board starts over from the side it is nearer to; one to [position] itself carries on.
     * With [instant] (reduced motion) it lands at once.
     */
    fun moveTo(position: Int, instant: Boolean) {
        wanted = position
        if (held) return
        val settling = motion?.isActive == true
        when {
            instant -> {
                motion?.cancel()
                from = position
                to = position
                travel = 0f
            }
            from == to && position == to -> Unit
            position == to -> if (!(settling && arriving)) settle(arrive = true, speed = 0f)
            position == from -> if (!(settling && !arriving)) settle(arrive = false, speed = 0f)
            else -> {
                if (travel >= HALFWAY) from = to
                to = position
                travel = 0f
                settle(arrive = true, speed = 0f)
            }
        }
    }

    // A finger scrubs between neighbours only: a move across several boards is caught on the side it is nearer to.
    private fun grab() {
        held = true
        motion?.cancel()
        if (abs(to - from) > 1) {
            if (travel >= HALFWAY) from = to else to = from
            travel = 0f
        }
        grabbedAt = travel
        grabbedTo = to
    }

    private fun settle(arrive: Boolean, speed: Float) {
        motion?.cancel()
        arriving = arrive
        motion = scope.launch {
            animate(travel, if (arrive) 1f else 0f, speed, MotionTokens.Swipe.Settle) { value, _ -> travel = value }
            if (arrive) from = to else to = from
            travel = 0f
            if (from != wanted) moveTo(wanted, instant = false)
        }
    }
}

/** The hub's scrub, following [position]: a board picked anywhere (a swipe, the title menu, the tablet's list) morphs to it. */
@Composable
internal fun rememberBoardScrub(position: Int): BoardScrub {
    val scope = rememberCoroutineScope()
    val scrub = remember { BoardScrub(position, scope) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(position, reduced) { scrub.moveTo(position, reduced) }
    return scrub
}

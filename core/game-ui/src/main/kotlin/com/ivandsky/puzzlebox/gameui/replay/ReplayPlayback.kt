package com.ivandsky.puzzlebox.gameui.replay

import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.game.replay.Timeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Headless player behind every replay surface. Both sides of a compare share one clock and one
 * position. It holds no Compose, no coroutine and no clock: [advance] is the only source of time.
 * With no replay on either side the state is empty and every call leaves it alone.
 */
class ReplayPlayback(
    private val mine: Timeline?,
    private val theirs: Timeline?,
    /** The duel's verdict. It replaces the computed lead once both sides ended. */
    private val finalLead: Lead? = null,
    showErrors: Boolean = true,
) {
    private val durationMs = maxOf(mine?.endMs ?: 0L, theirs?.endMs ?: 0L)
    private val ticksMs = (mine ?: theirs)?.moveTimesMs ?: LongArray(0)
    private val tickKinds = (mine ?: theirs)?.moveKindsAt ?: emptyArray()

    private val _state = MutableStateFlow(stateAt(durationMs, playing = false, speed = 1, skipIdle = true, showErrors = showErrors))
    val state: StateFlow<ReplayPlaybackState> = _state.asStateFlow()

    // The skip-idle travel in flight: from [travelFromMs] to [travelToMs] (-1 = none), [travelElapsedMs] of [travelMs] spent.
    // Wall time, so it ignores the speed. Pause freezes it and play continues it; any jump of the position drops it.
    private var travelFromMs = 0L
    private var travelToMs = NO_TRAVEL
    private var travelMs = 0L
    private var travelElapsedMs = 0L

    /** At the end it starts over from 0. */
    fun play() = change {
        if (it.positionMs == durationMs) {
            travelToMs = NO_TRAVEL
            it.with(positionMs = 0, playing = true)
        } else {
            it.with(playing = true)
        }
    }

    fun pause() = change { it.with(playing = false) }

    /** 1, 2, 4 or 8. Any other value is ignored. */
    fun setSpeed(x: Int) = change { if (x in SPEEDS) it.with(speed = x) else it }

    fun setSkipIdle(on: Boolean) = change {
        if (!on) travelToMs = NO_TRAVEL
        it.with(skipIdle = on)
    }

    /** Hides or shows every rule-violation mark; the position and the clock stay. */
    fun setShowErrors(on: Boolean) = change { it.with(showErrors = on) }

    /** Pauses on the next tick; past the last tick, on the end. */
    fun nextMove() = change {
        travelToMs = NO_TRAVEL
        it.with(positionMs = ticksMs.firstAbove(it.positionMs) ?: durationMs, playing = false)
    }

    /** Pauses on the tick before; before the first tick, on 0. */
    fun previousMove() = change {
        travelToMs = NO_TRAVEL
        it.with(positionMs = ticksMs.lastBelow(it.positionMs) ?: 0L, playing = false)
    }

    /**
     * Clamps [ms] into the replay. A playing replay keeps playing, but a seek to the end pauses there, as [advance] does.
     * The frame a seek lands on is [ReplayPlaybackState.seeked]: it changes the board with no motion, until the next [advance].
     */
    fun seekTo(ms: Long) = change {
        travelToMs = NO_TRAVEL
        val positionMs = ms.coerceIn(0, durationMs)
        it.with(positionMs = positionMs, playing = it.playing && positionMs < durationMs, seeked = it.seeked || positionMs != it.positionMs)
    }

    /**
     * A playing replay moves on by [frameDtMs] times the speed and pauses at the end. Paused, nothing moves.
     * With skip idle on, a gap that [startTravel] picks is crossed by a steady travel of wall time instead of
     * being played. It ends 1 s before the next move, so no board sees a new frame meanwhile.
     */
    fun advance(frameDtMs: Long) = change {
        if (!it.playing) return@change it
        if (travelToMs != NO_TRAVEL) return@change travelStep(it, frameDtMs)
        val reachedMs = (it.positionMs + frameDtMs * it.speed).coerceAtLeast(0)
        when {
            reachedMs >= durationMs -> it.with(positionMs = durationMs, playing = false, seeked = false)
            else -> {
                if (it.skipIdle) startTravel(reachedMs, it.speed)
                it.with(positionMs = reachedMs, seeked = false)
            }
        }
    }

    private fun travelStep(state: ReplayPlaybackState, frameDtMs: Long): ReplayPlaybackState {
        travelElapsedMs = minOf(travelMs, travelElapsedMs + frameDtMs.coerceAtLeast(0))
        if (travelElapsedMs == travelMs) {
            val targetMs = travelToMs
            travelToMs = NO_TRAVEL
            return state.with(positionMs = targetMs, seeked = false)
        }
        val done = travelElapsedMs.toDouble() / travelMs
        val left = 1.0 - done
        // Long travels run at constant speed; the short one eases out.
        val eased = if (travelMs == GLIDE_MS) 1.0 - left * left * left else done
        return state.with(positionMs = travelFromMs + ((travelToMs - travelFromMs) * eased).toLong(), seeked = false)
    }

    private inline fun change(next: (ReplayPlaybackState) -> ReplayPlaybackState) {
        if (mine == null && theirs == null) return
        _state.update(next)
    }

    /**
     * Starts a travel from [positionMs] to the lead-in of the next move (either side) when that move is more
     * than the idle gap away and the travel is faster than playing the gap. The travel lasts [GLIDE_MS], or
     * longer right after a move: it ends when that move's [DWELL_MS] of wall time is over, so the animation
     * gets its full time and the needle never stands still. Before the first move it is [GLIDE_MS].
     */
    private fun startTravel(positionMs: Long, speed: Int) {
        // After the last move of both sides nothing is left to travel to: the tail plays out.
        val nextMoveMs = nextMoveAfter(positionMs) ?: return
        if (nextMoveMs - positionMs <= IDLE_GAP_MS) return
        val targetMs = nextMoveMs - LEAD_IN_MS
        val lastMoveMs = lastMoveAtOrBefore(positionMs)
        val ms = if (lastMoveMs == null) GLIDE_MS else maxOf(GLIDE_MS, DWELL_MS - (positionMs - lastMoveMs) / speed)
        // Playing the gap takes (target - position) / speed wall ms: travel only when that is longer.
        if (targetMs - positionMs <= ms * speed) return
        travelFromMs = positionMs
        travelToMs = targetMs
        travelMs = ms
        travelElapsedMs = 0
    }

    private fun lastMoveAtOrBefore(positionMs: Long): Long? {
        val mineMs = mine?.moveTimesMs?.lastAtOrBelow(positionMs)
        val theirsMs = theirs?.moveTimesMs?.lastAtOrBelow(positionMs)
        return if (mineMs != null && theirsMs != null) maxOf(mineMs, theirsMs) else mineMs ?: theirsMs
    }

    private fun nextMoveAfter(positionMs: Long): Long? {
        val mineMs = mine?.moveTimesMs?.firstAbove(positionMs)
        val theirsMs = theirs?.moveTimesMs?.firstAbove(positionMs)
        return if (mineMs != null && theirsMs != null) minOf(mineMs, theirsMs) else mineMs ?: theirsMs
    }

    private fun ReplayPlaybackState.with(
        positionMs: Long = this.positionMs,
        playing: Boolean = this.playing,
        speed: Int = this.speed,
        skipIdle: Boolean = this.skipIdle,
        showErrors: Boolean = this.showErrors,
        seeked: Boolean = this.seeked,
    ): ReplayPlaybackState {
        val unchanged = positionMs == this.positionMs && playing == this.playing &&
            speed == this.speed && skipIdle == this.skipIdle && showErrors == this.showErrors && seeked == this.seeked
        // The state has identity equality, so only the same object keeps the flow from emitting.
        return if (unchanged) this else stateAt(positionMs, playing, speed, skipIdle, showErrors, seeked)
    }

    private fun stateAt(
        positionMs: Long,
        playing: Boolean,
        speed: Int,
        skipIdle: Boolean,
        showErrors: Boolean,
        seeked: Boolean = false,
    ): ReplayPlaybackState {
        val mineFrame = mine?.at(positionMs)
        val theirsFrame = theirs?.at(positionMs)
        val lead = if (mineFrame != null && theirsFrame != null) leadOf(mineFrame, theirsFrame) else null
        return ReplayPlaybackState(positionMs, durationMs, playing, speed, skipIdle, ticksMs, tickKinds, mineFrame, theirsFrame, lead, showErrors, seeked)
    }

    private fun leadOf(mineFrame: ReplayFrame, theirsFrame: ReplayFrame): Lead = when {
        !mineFrame.ended || !theirsFrame.ended -> Lead.of(mineFrame.facts, theirsFrame.facts)
        // An ended frame sits at its timeline's end, so its atMs is that side's end time.
        else -> finalLead ?: Lead.ended(mineFrame.facts, mineFrame.atMs, theirsFrame.facts, theirsFrame.atMs)
    }

    private companion object {
        val SPEEDS = intArrayOf(1, 2, 4, 8)
        const val IDLE_GAP_MS = 6_000L
        const val LEAD_IN_MS = 1_000L

        /** Shortest wall time the needle takes to cross an idle gap (ease-out cubic; longer travels are linear). */
        const val GLIDE_MS = 400L
        const val NO_TRAVEL = -1L

        /**
         * Wall time a move's board animation needs; the needle's travel to the next move ends when it is over. The worst replay step
         * is Strands' (the other four games peak at 840 ms), and the worst Strands step is its longest word:
         * a published word is 4..24 letters (`StrandsPublishGates.WORD_LENGTHS`), and a replay step draws the
         * recorded path in at ReplayDrawInLetterMs (90) per letter, then hands over to the word's animation.
         *  - Theme word of 24 letters: draw-in 24 x 90 = 2 160, then FoundSettle: letter n starts n x
         *    SettleStaggerMs (50) in, runs SettleMs (1 000), so the last (n = 23) ends at 23 x 50 + 1 000
         *    = 2 150. Total 4 310.
         *  - Spangram of 24 letters: draw-in 2 160, then the sweep. Its last letter starts at most
         *    SweepMaxMs (900) in (the stagger is squeezed, StrandsMotion.sweepDelayMs) and runs the same
         *    1 000 settle. The sweep and the settle are one overlapped animation (per-letter delay plus
         *    settle, FoundSpangramSpec.motionMs), not two in a row: 900 + 1 000 = 1 900. Total 4 060.
         *  - Bonus word of 24 letters: 2 160 + ReplayBonusHoldMs 1 000 + DissolveDuration 160 = 3 320.
         * Worst case 4 310 ms, plus 390 ms of slack for the first frames after the move stamp = 4 700 ms.
         * The longest word in the fixture puzzles has 13 letters (about 2 700 ms).
         */
        const val DWELL_MS = 4_700L
    }
}

/** The first stamp above [ms] in ascending distinct stamps. */
private fun LongArray.firstAbove(ms: Long): Long? {
    val found = binarySearch(ms)
    return getOrNull(if (found >= 0) found + 1 else insertionPoint(found))
}

/** The last stamp at or below [ms] in ascending distinct stamps. */
private fun LongArray.lastAtOrBelow(ms: Long): Long? {
    val found = binarySearch(ms)
    return getOrNull(if (found >= 0) found else insertionPoint(found) - 1)
}

/** The last stamp below [ms] in ascending distinct stamps. */
private fun LongArray.lastBelow(ms: Long): Long? {
    val found = binarySearch(ms)
    return getOrNull(if (found >= 0) found - 1 else insertionPoint(found) - 1)
}

/** binarySearch answers a miss with `-(insertion point) - 1`. */
private fun insertionPoint(missed: Int): Int = -(missed + 1)

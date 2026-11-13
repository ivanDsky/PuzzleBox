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

    /** At the end it starts over from 0. */
    fun play() = change {
        if (it.positionMs == durationMs) it.with(positionMs = 0, playing = true) else it.with(playing = true)
    }

    fun pause() = change { it.with(playing = false) }

    /** 1, 2, 4 or 8. Any other value is ignored. */
    fun setSpeed(x: Int) = change { if (x in SPEEDS) it.with(speed = x) else it }

    fun setSkipIdle(on: Boolean) = change { it.with(skipIdle = on) }

    /** Hides or shows every rule-violation mark; the position and the clock stay. */
    fun setShowErrors(on: Boolean) = change { it.with(showErrors = on) }

    /** Pauses on the next tick; past the last tick, on the end. */
    fun nextMove() = change {
        it.with(positionMs = ticksMs.firstAbove(it.positionMs) ?: durationMs, playing = false)
    }

    /** Pauses on the tick before; before the first tick, on 0. */
    fun previousMove() = change {
        it.with(positionMs = ticksMs.lastBelow(it.positionMs) ?: 0L, playing = false)
    }

    /** Clamps [ms] into the replay. A playing replay keeps playing, but a seek to the end pauses there, as [advance] does. */
    fun seekTo(ms: Long) = change {
        val positionMs = ms.coerceIn(0, durationMs)
        it.with(positionMs = positionMs, playing = it.playing && positionMs < durationMs)
    }

    /** A playing replay moves on by [frameDtMs] times the speed and pauses at the end. Paused, nothing moves. */
    fun advance(frameDtMs: Long) = change {
        if (!it.playing) return@change it
        val reachedMs = (it.positionMs + frameDtMs * it.speed).coerceAtLeast(0)
        when {
            reachedMs >= durationMs -> it.with(positionMs = durationMs, playing = false)
            it.skipIdle -> it.with(positionMs = pastIdle(reachedMs))
            else -> it.with(positionMs = reachedMs)
        }
    }

    private inline fun change(next: (ReplayPlaybackState) -> ReplayPlaybackState) {
        if (mine == null && theirs == null) return
        _state.update(next)
    }

    /** [positionMs], or the lead-in of the next move when that move is more than the idle gap away. */
    private fun pastIdle(positionMs: Long): Long {
        // After the last move of both sides nothing is left to jump to: the tail plays out.
        val nextMoveMs = nextMoveAfter(positionMs) ?: return positionMs
        return if (nextMoveMs - positionMs > IDLE_GAP_MS) nextMoveMs - LEAD_IN_MS else positionMs
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
    ): ReplayPlaybackState {
        val unchanged = positionMs == this.positionMs && playing == this.playing &&
            speed == this.speed && skipIdle == this.skipIdle && showErrors == this.showErrors
        // The state has identity equality, so only the same object keeps the flow from emitting.
        return if (unchanged) this else stateAt(positionMs, playing, speed, skipIdle, showErrors)
    }

    private fun stateAt(positionMs: Long, playing: Boolean, speed: Int, skipIdle: Boolean, showErrors: Boolean): ReplayPlaybackState {
        val mineFrame = mine?.at(positionMs)
        val theirsFrame = theirs?.at(positionMs)
        val lead = if (mineFrame != null && theirsFrame != null) leadOf(mineFrame, theirsFrame) else null
        return ReplayPlaybackState(positionMs, durationMs, playing, speed, skipIdle, ticksMs, tickKinds, mineFrame, theirsFrame, lead, showErrors)
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
    }
}

/** The first stamp above [ms] in ascending distinct stamps. */
private fun LongArray.firstAbove(ms: Long): Long? {
    val found = binarySearch(ms)
    return getOrNull(if (found >= 0) found + 1 else insertionPoint(found))
}

/** The last stamp below [ms] in ascending distinct stamps. */
private fun LongArray.lastBelow(ms: Long): Long? {
    val found = binarySearch(ms)
    return getOrNull(if (found >= 0) found - 1 else insertionPoint(found) - 1)
}

/** binarySearch answers a miss with `-(insertion point) - 1`. */
private fun insertionPoint(missed: Int): Int = -(missed + 1)

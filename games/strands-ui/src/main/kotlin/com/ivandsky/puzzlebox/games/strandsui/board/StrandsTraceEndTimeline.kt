package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.animation.core.LinearEasing
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import com.ivandsky.puzzlebox.games.strandsui.TraceEndKind

/**
 * One instant of a submitted path leaving the board: red when [error], else neutral; its
 * sideways [shake] in units of [MotionTokens.ErrorShake]; the layer's [alpha] and [scale].
 */
internal data class TraceEndLook(val error: Boolean, val shake: Float, val alpha: Float, val scale: Float)

/** The path's end as a pure timeline: the look at any elapsed time, null once it is gone, so tests read it without a clock. */
internal object TraceEndTimeline {

    fun look(kind: TraceEndKind, elapsedMs: Int, reduceMotion: Boolean, pulse: Boolean = false, hold: Int? = null): TraceEndLook? {
        val error = kind == TraceEndKind.Error
        if (elapsedMs >= totalMs(kind, reduceMotion, hold)) return null
        if (reduceMotion) return TraceEndLook(error, shake = 0f, alpha = 1f, scale = 1f)
        val held = if (error) StrandsMotion.ErrorShakeMs else hold ?: holdMs(kind)
        val pulsed = if (pulse) pulseScale(elapsedMs) else 1f
        if (elapsedMs < held) {
            val shake = if (error) shakeAt(elapsedMs.toFloat() / StrandsMotion.ErrorShakeMs) else 0f
            return TraceEndLook(error, shake, alpha = 1f, scale = pulsed)
        }
        val progress = (elapsedMs - held).toFloat() / dissolveMs(kind)
        val eased = MotionTokens.EasingEmphasizedAccelerate.transform(progress.coerceIn(0f, 1f))
        return TraceEndLook(error, shake = 0f, alpha = 1f - eased, scale = (1f - (1f - MotionTokens.DissolveScale) * eased) * pulsed)
    }

    /** [hold] overrides the kind's own hold of a set-aside path (the replay board's). */
    fun totalMs(kind: TraceEndKind, reduceMotion: Boolean, hold: Int? = null): Int = when {
        reduceMotion && kind == TraceEndKind.Error -> MotionTokens.ErrorReducedHold
        reduceMotion && kind == TraceEndKind.Bonus -> StrandsMotion.ReducedBonusHoldMs
        reduceMotion -> maxOf(holdMs(kind), StrandsMotion.ReducedHoldMinMs)
        kind == TraceEndKind.Error -> MotionTokens.ErrorDuration
        else -> (hold ?: holdMs(kind)) + MotionTokens.DissolveDuration
    }

    private fun holdMs(kind: TraceEndKind): Int = when (kind) {
        TraceEndKind.Error -> 0
        TraceEndKind.Bonus -> StrandsMotion.BonusHoldMs
        TraceEndKind.Short -> StrandsMotion.ShortHoldMs
        TraceEndKind.Repeat -> StrandsMotion.RepeatHoldMs
    }

    /** A refused path's dissolve takes what the shake leaves of the error's total; a set-aside path's its own token. */
    private fun dissolveMs(kind: TraceEndKind): Int =
        if (kind == TraceEndKind.Error) MotionTokens.ErrorDuration - StrandsMotion.ErrorShakeMs else MotionTokens.DissolveDuration

    private val ShakeAt = FloatArray(StrandsMotion.ShakeStops.size) { it.toFloat() / StrandsMotion.ShakeStops.lastIndex }

    /** A repeated theme word's tiles: 1 → [StrandsMotion.PulsePeak] → 1 over the expressive duration, 1 once it is over. */
    fun pulseScale(elapsedMs: Int): Float {
        val progress = elapsedMs.toFloat() / MotionTokens.DurationExpressive
        if (progress !in 0f..1f) return 1f
        val peak = StrandsMotion.PulseAt
        val rise = if (progress < peak) progress / peak else 1f - (progress - peak) / (1f - peak)
        return 1f + (StrandsMotion.PulsePeak - 1f) * MotionTokens.EasingStandard.transform(rise)
    }

    private fun shakeAt(progress: Float): Float = keyframes(progress, ShakeAt, StrandsMotion.ShakeStops, LinearEasing)
}

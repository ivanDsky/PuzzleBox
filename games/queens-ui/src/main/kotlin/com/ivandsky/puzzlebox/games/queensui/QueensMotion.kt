package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlin.math.abs
import kotlin.math.max

/**
 * Spec A13's satisfaction motion as pure curves of elapsed time: queen Pop in, auto-cross
 * Stagger, region Pulse, clash Shake and the solve Orchestration (crosses Fade out →
 * queens Pulse in placement order → the shared SolveSweep + Stamp). Timings are page 06's
 * variant C/D. Values with a MotionTokens job reuse it; the rest are Queens-only and stay
 * here until a second game needs them.
 */
internal object QueensMotion {

    // Pop in: 0.5 → 1.15 → 0.98 → 1 over the whole run, fading in by the overshoot.
    const val POP_MS = 280
    private const val POP_FROM = 0.5f
    private const val POP_PEAK = 1.15f
    private const val POP_PEAK_AT = 0.55f
    private const val POP_DIP = 0.98f
    private const val POP_DIP_AT = 0.8f
    private const val POP_FADE_TO = 0.4f

    // Stagger: auto crosses grow 0.6 → 1 ring by ring outward from the new queen.
    const val STAGGER_STEP_MS = 22L
    private const val STAGGER_CAP_MS = 154L
    const val STAGGER_ENTER_MS = 160
    const val STAGGER_FROM = 0.6f

    // Pulse: the queen's region washes toward white and back, rippling out from it.
    private const val PULSE_MS = MotionTokens.PulseDuration
    private const val PULSE_STEP_MS = 28
    private const val PULSE_CAP_MS = 196
    private const val WASH_PEAK_AT = 0.3f
    const val WASH_PEAK_LIGHT = 0.55f
    const val WASH_PEAK_DARK = 0.22f

    // Shake: the clashing queens jolt sideways and settle.
    private val SHAKE_MS = MotionTokens.DurationMedium
    private val SHAKE_DP = floatArrayOf(0f, -5f, 4f, -2f, 0f)

    // Orchestration: crosses fade out from the last queen, then each queen pulses.
    const val CROSS_FADE_MS = 160L
    private const val CROSS_STEP_MS = 20L
    const val CROSS_CAP_MS = 140L
    private const val CROSS_TO = 0.6f
    const val QUEEN_LEAD_MS = 200L
    const val QUEEN_STEP_MS = 70L
    private const val QUEEN_PULSE_MS = 300L
    private const val QUEEN_PULSE_SCALE = 1.22f
    private const val QUEEN_PULSE_PEAK_AT = 0.4f
    const val SWEEP_GAP_MS = 100L

    /** Chebyshev ring of [idx] around [origin] on an [n]-wide board. */
    fun ring(idx: Int, origin: Int, n: Int): Int =
        max(abs(idx / n - origin / n), abs(idx % n - origin % n))

    /** How long the auto cross at [idx] waits after the queen at [origin] lands. */
    fun staggerDelayMs(idx: Int, origin: Int?, n: Int): Long =
        if (origin == null) 0L else (ring(idx, origin, n) * STAGGER_STEP_MS).coerceAtMost(STAGGER_CAP_MS)

    /** How long the solve frame waits so the sweep starts after the last queen's Pulse begins. */
    fun solveLeadMs(queens: Int, reducedMotion: Boolean): Long =
        if (reducedMotion) 0L else QUEEN_LEAD_MS + queens * QUEEN_STEP_MS + SWEEP_GAP_MS

    fun popScale(elapsedMs: Float): Float {
        val t = MotionTokens.EasingEmphasizedDecelerate.transform(progress(elapsedMs, POP_MS.toFloat()))
        return when {
            t < POP_PEAK_AT -> lerp(POP_FROM, POP_PEAK, t / POP_PEAK_AT)
            t < POP_DIP_AT -> lerp(POP_PEAK, POP_DIP, (t - POP_PEAK_AT) / (POP_DIP_AT - POP_PEAK_AT))
            else -> lerp(POP_DIP, 1f, (t - POP_DIP_AT) / (1f - POP_DIP_AT))
        }
    }

    /** Pop in's fade: linear in time, so the queen is solid by the time its scale peaks. */
    fun popAlpha(elapsedMs: Float): Float = (progress(elapsedMs, POP_MS.toFloat()) / POP_FADE_TO).coerceAtMost(1f)

    fun pulseTotalMs(maxRing: Int): Int = (maxRing * PULSE_STEP_MS).coerceAtMost(PULSE_CAP_MS) + PULSE_MS

    /** Region Pulse intensity (0..1) of a cell [ring] rings from the queen. */
    fun pulseWash(elapsedMs: Float, ring: Int): Float {
        val delay = (ring * PULSE_STEP_MS).coerceAtMost(PULSE_CAP_MS)
        return wash(elapsedMs - delay, PULSE_MS.toFloat())
    }

    fun shakeTotalMs(): Int = SHAKE_MS

    /** Clash Shake offset in dp: keyframes evenly spaced over the run. */
    fun shakeDp(elapsedMs: Float): Float {
        val t = MotionTokens.EasingStandard.transform(progress(elapsedMs, SHAKE_MS.toFloat()))
        val segments = SHAKE_DP.size - 1
        val at = (t * segments).coerceAtMost(segments - 1e-4f)
        val i = at.toInt()
        return lerp(SHAKE_DP[i], SHAKE_DP[i + 1], at - i)
    }

    fun solveTotalMs(queens: Int): Long =
        max(CROSS_CAP_MS + CROSS_FADE_MS, QUEEN_LEAD_MS + (queens - 1).coerceAtLeast(0) * QUEEN_STEP_MS + QUEEN_PULSE_MS)

    /** A cross's presence (1 → 0) during the orchestration, [ring] rings from the last queen. */
    fun solveCross(elapsedMs: Float, ring: Int): Float {
        val delay = (ring * CROSS_STEP_MS).coerceAtMost(CROSS_CAP_MS)
        val t = progress(elapsedMs - delay, CROSS_FADE_MS.toFloat())
        return 1f - MotionTokens.EasingEmphasizedAccelerate.transform(t)
    }

    /** A cross's scale while it fades out, 1 → [CROSS_TO] as presence falls. */
    fun solveCrossScale(presence: Float): Float = lerp(CROSS_TO, 1f, presence)

    /** The [order]-th placed queen's Pulse scale during the orchestration. */
    fun solveQueenScale(elapsedMs: Float, order: Int): Float {
        val t = MotionTokens.EasingStandard.transform(queenProgress(elapsedMs, order))
        return if (t <= 0f || t >= 1f) {
            1f
        } else if (t < QUEEN_PULSE_PEAK_AT) {
            lerp(1f, QUEEN_PULSE_SCALE, t / QUEEN_PULSE_PEAK_AT)
        } else {
            lerp(QUEEN_PULSE_SCALE, 1f, (t - QUEEN_PULSE_PEAK_AT) / (1f - QUEEN_PULSE_PEAK_AT))
        }
    }

    /** The [order]-th placed queen's wash during its Pulse. */
    fun solveQueenWash(elapsedMs: Float, order: Int): Float =
        wash(elapsedMs - (QUEEN_LEAD_MS + order * QUEEN_STEP_MS), QUEEN_PULSE_MS.toFloat())

    private fun queenProgress(elapsedMs: Float, order: Int): Float =
        progress(elapsedMs - (QUEEN_LEAD_MS + order * QUEEN_STEP_MS), QUEEN_PULSE_MS.toFloat())

    /** Rises to 1 at [WASH_PEAK_AT] of [durationMs], then decays back to 0. */
    private fun wash(localMs: Float, durationMs: Float): Float {
        if (localMs <= 0f || localMs >= durationMs) return 0f
        val t = MotionTokens.EasingStandard.transform(localMs / durationMs)
        return if (t < WASH_PEAK_AT) t / WASH_PEAK_AT else (1f - t) / (1f - WASH_PEAK_AT)
    }

    private fun progress(elapsedMs: Float, durationMs: Float): Float = (elapsedMs / durationMs).coerceIn(0f, 1f)

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t.coerceIn(0f, 1f)
}

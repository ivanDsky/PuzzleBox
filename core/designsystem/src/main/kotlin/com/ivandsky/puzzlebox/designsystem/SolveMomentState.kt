package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One board's solve moment (M12-10) on one clock: the milliseconds since the solve. The sweep band, the board pop and
 * the frame line with its comet are all read off it, so they cannot drift apart. [play] runs it once from the solve;
 * the motion is over when the frame closes, [CelebrationMotion.FrameDrawStartMs] + [CelebrationMotion.FrameDrawMs]
 * after it, and the closed frame stays until [reset]. Hold it with [rememberSolveMomentState] and draw it with
 * [solveFrame]. [atMs] shows the moment frozen that long after the solve, for a preview.
 */
@Stable
class SolveMomentState internal constructor(atMs: Int? = null) {
    /** False until a solve plays: the board is drawn as it is. */
    internal var playing by mutableStateOf(atMs != null)
        private set

    private val clock = Animatable(atMs?.toFloat() ?: 0f)

    /** Reduced motion, or a preview: [play] goes straight to the closed frame. Kept current by [rememberSolveMomentState]. */
    internal var instant = false

    /** Milliseconds since the solve, held at the end of the motion. Read it in the draw phase. */
    internal val ms: Float get() = clock.value

    /** The board's scale now. Read it in a layer block. */
    internal val popScale: Float get() = if (playing) solvePopScale(clock.value) else 1f

    /** Plays the moment from the solve and returns when its motion is over. */
    suspend fun play() {
        clock.snapTo(if (instant) MOTION_END_MS.toFloat() else 0f)
        playing = true
        if (!instant) clock.animateTo(MOTION_END_MS.toFloat(), tween(MOTION_END_MS, easing = LinearEasing))
    }

    /** Back to the board as it is, for a fresh board in the same place. */
    fun reset() {
        playing = false
    }
}

private const val MOTION_END_MS = CelebrationMotion.FrameDrawStartMs + CelebrationMotion.FrameDrawMs
private val PopRise = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/** The Stamp spring taking the board from the pop back to rest; within half a thousandth of rest it is over. */
private val PopRelease = FloatSpringSpec(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness, visibilityThreshold = 0.0005f)
private val PopReleaseNanos = PopRelease.getDurationNanos(CelebrationMotion.BoardPopScale, 1f, 0f)

/** The board's scale [ms] after the solve: 1 until the pop starts, up to the peak on an ease-out, then the spring home. */
internal fun solvePopScale(ms: Float): Float {
    val rising = ms - CelebrationMotion.BoardPopStartMs
    if (rising <= 0f) return 1f
    val releasing = rising - CelebrationMotion.BoardPopRiseMs
    if (releasing < 0f) {
        return 1f + (CelebrationMotion.BoardPopScale - 1f) * PopRise.transform(rising / CelebrationMotion.BoardPopRiseMs)
    }
    val nanos = (releasing * 1_000_000f).toLong()
    return if (nanos >= PopReleaseNanos) 1f else PopRelease.getValueFromNanos(nanos, CelebrationMotion.BoardPopScale, 1f, 0f)
}

/** How much of the frame line is drawn [ms] after the solve, 0..1 of the lap. */
internal fun solveFrameLap(ms: Float): Float =
    ((ms - CelebrationMotion.FrameDrawStartMs) / CelebrationMotion.FrameDrawMs).coerceIn(0f, 1f)

/** The comet's alpha when [lap] of the frame line is drawn: whole, then fading out as the lap closes. */
internal fun solveCometAlpha(lap: Float): Float = ((1f - lap) / CelebrationMotion.FrameCometFadeShare).coerceIn(0f, 1f)

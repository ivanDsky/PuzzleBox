package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.ivandsky.puzzlebox.designsystem.MotionTokens

/**
 * How the animated board moves. Only position is slow: a row glides to its place, makes room and closes up, and the
 * place number and the time roll to their new values, all on one no-bounce spring that settles in about [GlideMillis].
 * Fades and medals keep the app's normal timing, so a row that arrives or leaves is quick and its neighbours then
 * follow slowly, which is what lets the player follow who moved where.
 */
internal object BoardMotion {
    // Stiffness 50 on a critically damped spring settles in about 1.2 s.
    private const val GlideStiffness = 50f
    const val GlideMillis = 1200

    /** Each reordered row starts this much after the one ahead of it in the new order, up to [StaggerCap] rows. */
    const val StaggerMillis = 70
    const val StaggerCap = 8

    /** An arriving row waits a fifth of the glide, at most this long, so the rows that make room for it have set off. */
    const val ArrivalDelayMillis = 200

    /** A medal flips in once the roll is nearly there: 0.4 of the glide after the row starts to move. */
    const val MedalLeadMillis = 480

    // A slide is in pixels, an opening is a fraction of the row's height, a roll is in cells: each settles at a distance nobody sees.
    val slide = spring<Float>(MotionTokens.SpringDampingDefault, GlideStiffness, visibilityThreshold = 0.5f)
    val open = spring<Float>(MotionTokens.SpringDampingDefault, GlideStiffness, visibilityThreshold = 0.002f)
    val roll = open

    // A time rolls in milliseconds and shows whole seconds, so half a second is as close as it needs to settle.
    val count = spring<Float>(MotionTokens.SpringDampingDefault, GlideStiffness, visibilityThreshold = 500f)

    // A row fades in as it opens, and out quicker than it closes, so it is gone before the rows meet.
    val fadeIn = tween<Float>(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)
    val fadeOut = tween<Float>(MotionTokens.DurationExit, easing = MotionTokens.EasingStandard)

    // The empty-board message waits for the leaving rows to be gone, then fades in.
    val messageIn = tween<Float>(MotionTokens.EnterDuration, MotionTokens.DurationExit, MotionTokens.EasingEmphasizedDecelerate)

    val medalIn = tween<Float>(MotionTokens.DurationLong, easing = MotionTokens.EasingEmphasizedDecelerate)
    val medalOut = tween<Float>(MotionTokens.DurationExit, easing = MotionTokens.EasingStandard)

    /** The wait of the [k]-th row to start moving (0 is the first), counted in the new order. */
    fun staggerMs(k: Int): Int = minOf(k, StaggerCap) * StaggerMillis
}

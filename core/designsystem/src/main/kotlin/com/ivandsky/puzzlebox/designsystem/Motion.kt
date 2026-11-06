package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Canonical motion vocabulary (A12): the app animates with shared-axis screen
// transitions, a container transform into a puzzle, and spring physics on
// interactions. Transitions are wired in later tickets; they consume these tokens
// so every screen moves on the same physics.
object MotionTokens {
    // Feedback stays under ~160ms; only full-screen patterns go longer.
    val DurationShort = 120
    val DurationMedium = 240
    val DurationLong = 300
    val DurationExpressive = 400

    // Screen-to-screen nav transitions (Nav3 default is 700ms — sluggish for tab switches).
    // Fade-through: exit fades out fast, enter starts after a delay — content from the two
    // screens never superimposes (emil-design-eng "two distinct objects" overlap fix;
    // tab switches aren't hierarchical, so the slide stays a residual 1/24).
    const val NavTransitionEnter = 200
    const val NavTransitionEnterDelay = 90
    const val NavTransitionExit = 90

    // Back is quicker than forward: the destination is a screen the user just left, so it
    // returns in ~180ms instead of ~300ms (S9 HITL: the 0.11 back felt slow).
    const val NavPopTravel = 180
    const val NavPopEnter = 120
    const val NavPopEnterDelay = 60
    const val NavPopExit = 60

    // Material 3 emphasized curves: decelerate on the way in, accelerate on the way out.
    val EasingStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EasingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EasingEmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val EasingLinear = LinearEasing

    // Critically damped by default (no overshoot); the underdamped variant is reserved
    // for gestures that carry momentum (flick, drag release). Stiffness ~380 matches an
    // Apple-style response of ~0.32s: quick without feeling twitchy.
    const val SpringDampingDefault = Spring.DampingRatioNoBouncy
    const val SpringDampingMomentum = 0.8f
    const val SpringStiffnessDefault = 380f

    // Identity §2.6: one name per job. Each job's reduced-motion fallback sits beside it;
    // "none" means the state change is instant (read rememberReducedMotion()).

    // Press: scale 0.97 on the default spring. Reduced: none.
    const val PressScale = 0.97f

    // Select: icon crossfade + nav indicator 32→56dp on EasingStandard. Reduced: instant.
    const val SelectDuration = 120

    // Enter (list items): rise + fade on EasingEmphasizedDecelerate, staggered per item
    // up to a cap. Reduced: a plain ReducedFadeDuration fade.
    const val EnterDuration = 240
    const val EnterStagger = 40
    const val EnterStaggerCap = 5
    val EnterRise: Dp = 8.dp

    // The one fade every reduced-motion fallback uses instead of travel.
    const val ReducedFadeDuration = 120

    // Pulse: the "next up" glyph 1→1.12→1, once per visit. Reduced: none.
    const val PulseDuration = 360
    const val PulseScale = 1.12f

    // CountUp: numbers roll on EasingEmphasizedDecelerate. Reduced: the final value.
    const val CountUpDuration = 400

    // SolveSweep: a diagonal accent band (a fraction of the board width, at a low alpha)
    // crosses the board TL→BR on EasingStandard; the board outline turns accent at a
    // thicker width. Reduced: the outline only, instantly.
    const val SweepDuration = 480
    const val SweepBandFraction = 0.4f
    const val SweepAlpha = 0.24f
    const val SweepOutlineDuration = 160
    val SweepOutlineWidth: Dp = 3.dp

    // Stamp: a glyph lands from 1.15× to 1 (spec A6 overrides identity's 0.6) on the app's
    // only bouncy spring, kept for wins, while it fades in. Reduced: the static glyph.
    const val StampFromScale = 1.15f
    const val StampSpringDamping = 0.55f
    const val StampSpringStiffness = 600f
    const val StampFadeDuration = 120

    // SolveHold: a solved lesson stays on screen this long before the next one. A pause,
    // not an animation, so it holds under reduced motion too.
    const val SolveHoldMillis = 900L

    // Reject: a refused move jolts the cell on a medium-bouncy spring, and the rejection
    // highlight clears after RejectHoldMillis. Reduced: the highlight without the jolt.
    const val RejectSpringDamping = Spring.DampingRatioMediumBouncy
    const val RejectSpringStiffness = Spring.StiffnessMediumLow
    const val RejectHoldMillis = 600L

    // Flash: a one-shot colour switch (Zip's next-target ring) that settles back; 300ms
    // read as instant (user round 6c). Reduced: settles instantly.
    const val FlashDuration = 700

    // HintFade: a placed hint mark holds, then fades out. Reduced: removed without a fade.
    const val HintHoldMillis = 2000L
    const val HintFadeDuration = 300

    // HintPulse: one half-cycle of a hint region's pulse (dim, or back). Reduced: none.
    const val HintPulseDuration = 400

    // Shimmer: a skeleton sheen sweeps start→end in a Linear loop. Reduced: static skeleton.
    const val ShimmerDuration = 1100

    // Cascade: a mass exit (Queens auto-marks) staggers per cell up to a cap, so the wave
    // reads as one orchestration. Reduced: instant.
    const val CascadeStepMillis = 10L
    const val CascadeCapMillis = 80L

    // Settle: newly introduced rule violations wait this long before they show.
    const val SettleMillis = 500L
}

// A named screen-level pattern. Exit is faster than enter: leaving feels snappy,
// arriving feels intentional.
data class MotionPattern(
    val name: String,
    val description: String,
    val enterDurationMillis: Int,
    val enterEasing: Easing,
    val exitDurationMillis: Int,
    val exitEasing: Easing,
)

val SharedAxisTransition = MotionPattern(
    name = "Shared axis",
    description = "Screen-to-screen navigation: old and new screens slide along one axis, staggered so the move reads as one gesture.",
    enterDurationMillis = MotionTokens.DurationLong,
    enterEasing = MotionTokens.EasingEmphasizedDecelerate,
    exitDurationMillis = MotionTokens.DurationMedium,
    exitEasing = MotionTokens.EasingEmphasizedAccelerate,
)

val ContainerTransform = MotionPattern(
    name = "Container transform",
    description = "A puzzle card grows into its full screen while the subject stays visible, connecting list and game.",
    enterDurationMillis = MotionTokens.DurationExpressive,
    enterEasing = MotionTokens.EasingStandard,
    exitDurationMillis = MotionTokens.DurationLong,
    exitEasing = MotionTokens.EasingStandard,
)

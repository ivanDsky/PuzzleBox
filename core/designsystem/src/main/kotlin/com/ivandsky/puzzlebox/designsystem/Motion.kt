package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Canonical motion vocabulary: the app animates with shared-axis screen
// transitions, a container transform into a puzzle, and spring physics on
// interactions. Transitions consume these tokens
// so every screen moves on the same physics.
//
// Leaderboard rows (move, arrive, leave) reuse these tokens rather than adding their own: moves settle on the default
// spring (as do the rows that open and close around them), an arriving row fades in over EnterDuration and a leaving
// one fades out over DurationShort. Under reduced motion every change lands without movement.
object MotionTokens {
    // Feedback stays under ~160ms; only full-screen patterns go longer.
    val DurationShort = 120
    val DurationMedium = 240
    val DurationLong = 300
    val DurationExpressive = 400

    // In-pane transitions (library pages, game feedback); screens move on the Nav* set below.
    // Fade-through: exit fades out fast, enter starts after a delay — content from the two
    // screens never superimposes (emil-design-eng "two distinct objects" overlap fix;
    // tab switches aren't hierarchical, so the slide stays a residual 1/24).
    const val NavTransitionEnter = 200
    const val NavTransitionEnterDelay = 90
    const val NavTransitionExit = 90

    // Back is quicker than forward: the destination is a screen the user just left, so it
    // returns in ~180ms instead of ~300ms (the 0.11 back felt slow).
    const val NavPopTravel = 180
    const val NavPopEnter = 120
    const val NavPopEnterDelay = 60
    const val NavPopExit = 60

    // Phone navigation (polish 04, variant B): a push slides the whole window in from the
    // end edge on top; the outgoing page, its bar with it, parallaxes a fraction of the
    // width under a scrim. Back mirrors it on the shorter standard clock. A tab switch is a
    // fade-through on the tab clock.
    const val NavFullSlideEnter = 380
    const val NavFullSlidePop = 280
    const val NavParallaxFraction = 0.28f
    const val NavScrimAlpha = 0.32f
    const val NavTabExit = 120
    const val NavTabEnter = 240
    const val NavTabEnterDelay = 100

    // Tablet navigation (polish 04, variant D): a game lifts out of its tapped card (container
    // transform); the screen is opaque throughout and the card's content fades off it over
    // the first MixLiftCardFade (pop: back on over 25–70%). The rail fades and shifts out by
    // LiftNavShift over the first LiftNavFraction, under LiftScrimAlpha over the list.
    val LiftEnter = DurationExpressive
    val LiftExit = DurationLong
    const val LiftNavFraction = 0.4f
    val LiftNavShift: Dp = 24.dp
    const val LiftScrimAlpha = 0.2f
    const val MixLiftCardFade = 0.45f

    // Game ⇄ result on tablet: both pages travel the full width together, opaque.
    const val MixAxisPush = 360
    const val MixAxisPop = 300

    // Tablet tab and list→detail: the incoming page fades in (and a tab rises) over the
    // outgoing one, which stays opaque — two pages never fade at once.
    const val MixTabIn = 220
    val MixTabRise: Dp = 16.dp

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

    // One name per job. Each job's reduced-motion fallback sits beside it;
    // "none" means the state change is instant (read rememberReducedMotion()).

    // Press: scale 0.97 on the default spring. Reduced: none.
    const val PressScale = 0.97f

    // Press, chunky game buttons (GameButton): sink — the face translates down into its
    // lip on the default spring instead of scaling. Reduced: instant.

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

    // CountUp, slower: a score that rolls up to a small whole-number tally, e.g. a duel's final score.
    const val ScoreCountUpDuration = 700

    // SolveSweep: a diagonal accent band (a fraction of the board width, at a low alpha)
    // crosses the board TL→BR on EasingStandard; the board outline turns accent at a
    // thicker width. Reduced: the outline only, instantly.
    const val SweepDuration = 480
    const val SweepBandFraction = 0.4f
    const val SweepAlpha = 0.24f
    const val SweepOutlineDuration = 160
    val SweepOutlineWidth: Dp = 3.dp

    // Stamp: a glyph lands from 1.15× to 1 (not the design identity's 0.6) on the app's
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

    // Shake: a refused entry (an unknown code, a publish that ends in a retry) is kicked
    // sideways from rest on the reject spring, launched at ShakeVelocityPerPx (1/s) times
    // its ShakeKick in px so the first swing peaks near the kick. Reduced: none.
    val ShakeKick: Dp = 6.dp
    const val ShakeVelocityPerPx = 36f
    val ShakeSpring: SpringSpec<Float> = spring(RejectSpringDamping, RejectSpringStiffness)

    // Flash: a one-shot colour switch (Zip's next-target ring) that settles back; 300ms
    // read as instant (user round 6c). Reduced: settles instantly.
    const val FlashDuration = 700

    // HintFade: a placed hint mark holds, then fades out. Reduced: removed without a fade.
    const val HintHoldMillis = 2000L
    const val HintFadeDuration = 300

    // Shimmer: a skeleton sheen sweeps start→end in a Linear loop. Reduced: static skeleton.
    const val ShimmerDuration = 1100

    // Cascade: a mass exit (Queens auto-marks) staggers per cell up to a cap, so the wave
    // reads as one orchestration. Reduced: instant.
    const val CascadeStepMillis = 10L
    const val CascadeCapMillis = 80L

    // Settle: newly introduced rule violations wait this long before they show.
    const val SettleMillis = 500L

    // Word feedback (Strands, prototype 03): a refused word's traced path turns red and
    // shakes for ErrorShakeShare of ErrorDuration, then dissolves in the rest; a word that
    // is only set aside clears neutral, dissolving over DissolveDuration to DissolveScale.
    // Reduced: the colour holds with no shake or dissolve, then clears.
    const val ErrorDuration = 320
    const val ErrorShakeShare = 0.72f
    val ErrorShake: Dp = 6.dp
    const val DissolveDuration = 160
    const val DissolveScale = 0.95f
    const val ErrorReducedHold = 600

    // Hint dot and "+1": a counter dot fills over DotFillDuration; a gained hint's "+1"
    // rises BonusRise over BonusRiseDuration. Reduced: the dot lands at once, the "+1" holds
    // still for BonusReducedHold.
    const val DotFillDuration = 240
    val BonusRise: Dp = 12.dp
    const val BonusRiseDuration = 640
    const val BonusReducedHold = 700

    // Trace (Zip + Strands): the line end leans from the last committed cell along
    // ONE legal direction toward the finger (never freehand, never into a refused cell) and
    // rests on the cell when the finger points anywhere else; a head disc rides the end.
    // Cells added grow in along the path and a backtrack retracts along it. Reduced: the tip
    // still leans; no grow, retract or home.
    object Trace {
        // Closer than this to the tail centre there is no tip, so a resting finger never jitters.
        const val TipDeadCells = 0.06f

        // The tip tracks the finger's push along its axis 1:1, never past this fraction of the
        // step: it can not reach the next centre before the cell commits.
        const val TipMaxCells = 0.9f

        // Head Ø as a multiple of the line width.
        const val HeadScale = 1.2f

        // Cell pop: 1 → PopScale → 1 over PopDuration; the rise takes PopRiseFraction on
        // EasingOut, the settle EasingStandard.
        const val PopScale = 1.08f
        const val PopDuration = 200
        const val PopRiseFraction = 0.4f

        // Cells added in one event grow in along the path; a backtrack retracts faster than it
        // grew (exit < enter); a tip let go returns to the tail centre faster still. Length
        // changes under BlendMinCells (the finger dragging the tip) follow instantly.
        const val GrowDuration = 100
        const val RetractDuration = 60
        const val HomeDuration = 60
        const val BlendMinCells = 0.3f
        val EasingOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
        val EasingRetract = EasingEmphasizedAccelerate
    }
}

/** Plays the trace pop ([MotionTokens.Trace]) from the current value: up to PopScale, then back to 1. */
suspend fun Animatable<Float, AnimationVector1D>.tracePop() {
    val rise = (MotionTokens.Trace.PopDuration * MotionTokens.Trace.PopRiseFraction).toInt()
    animateTo(MotionTokens.Trace.PopScale, tween(rise, easing = MotionTokens.Trace.EasingOut))
    animateTo(1f, tween(MotionTokens.Trace.PopDuration - rise, easing = MotionTokens.EasingStandard))
}

/** The trace pop's scale at [t] (0..1, clamped) through PopDuration, for frame-driven cells. */
fun tracePopScale(t: Float): Float {
    val p = t.coerceIn(0f, 1f)
    val rise = MotionTokens.Trace.PopRiseFraction
    val peak = MotionTokens.Trace.PopScale
    return if (p < rise) {
        1f + (peak - 1f) * MotionTokens.Trace.EasingOut.transform(p / rise)
    } else {
        peak + (1f - peak) * MotionTokens.EasingStandard.transform((p - rise) / (1f - rise))
    }
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

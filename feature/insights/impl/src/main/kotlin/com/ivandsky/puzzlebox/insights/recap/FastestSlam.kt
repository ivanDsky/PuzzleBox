package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One game's slam, beat for beat the prototype's `slam`: the card's top, meta line and replay pill rise in; the timer
 * climbs from 0:00 to the average and holds; the average peels off as a ghost shrinking up to its place, struck through,
 * while the timer drops to the fastest time; after a pause the time stamps down with a recoil and streaks, and the
 * replay pill pops and pulses. A game with no average climbs straight to its time and pops the pill. With [instant]
 * (reduced motion, a preview) every value starts at its end. The card reads the values in layer and draw blocks, so a
 * frame redraws and never recomposes.
 */
internal class Slam(private val timeMs: Long, private val averageMs: Long?, private val instant: Boolean) {
    private val end = if (instant) 1f else 0f
    val odometer = Odometer(timeMs, averageMs, instant)

    /** The rises of the card's top block, meta line and replay pill, and of the no-average line, 0..1. */
    val top = Animatable(end)
    val meta = Animatable(end)
    val cta = Animatable(end)
    val firstSolve = Animatable(end)

    /** The ghost average shows once the climb has held; [peel] takes it from over the timer (0) to its place (1). */
    var ghostShown by mutableStateOf(instant)
        private set
    val peel = Animatable(end)
    val strike = Animatable(end)
    val ghostLabel = Animatable(end)

    /** The timer's stamp scale, the card's recoil and the streaks' runs (0..1), and the replay pill's pop and pulse. */
    val stamp = Animatable(1f)
    val recoil = Animatable(0f)
    val streaks = List(STREAK_TOPS.size) { Animatable(0f) }
    val popAlpha = Animatable(1f)
    private val popScale = Animatable(1f)
    private val pulse = Animatable(0f)

    /** Where the ghost and the timer sit (before their own layers), so the peel starts exactly over the timer. */
    var ghostAt: LayoutCoordinates? = null
    var timerAt: LayoutCoordinates? = null

    suspend fun play(base: Int) {
        if (instant) return
        coroutineScope {
            rise(top, base)
            rise(meta, base + MotionTokens.EnterStagger * 3 / 2)
            rise(cta, base + MotionTokens.EnterStagger * 3)
            delay((base + ClimbLead).toLong())
            if (odometer.hours) launch { odometer.hourColon.animateTo(1f, tween(MotionTokens.DurationShort, easing = MotionTokens.EasingLinear)) }
            if (averageMs == null) {
                climb(timeMs)
                odometer.settled = true
                rise(firstSolve, 0)
                popIn()
                return@coroutineScope
            }
            climb(averageMs)
            delay(RecapMotion.SlamHold.toLong())
            ghostShown = true
            launch { peel.animateTo(1f, tween(RecapMotion.SlamPeel, easing = MotionTokens.EasingEmphasizedDecelerate)) }
            launch { strike.animateTo(1f, tween(MotionTokens.DurationMedium, StrikeDelay, MotionTokens.EasingStandard)) }
            launch { ghostLabel.animateTo(1f, tween(MotionTokens.EnterDuration, GhostLabelDelay, MotionTokens.EasingEmphasizedDecelerate)) }
            odometer.roll(timeMs, up = false, RecapMotion.SlamDrop, SlamDropStagger, MotionTokens.EasingEmphasizedAccelerate, RecapMotion.SlamOvershoot)
            launch { odometer.land() }
            delay(RecapMotion.SlamLandingPause.toLong())
            launch {
                stamp.snapTo(MotionTokens.StampFromScale)
                stamp.animateTo(1f, stampSpring)
            }
            launch { recoil.animateTo(1f, tween(MotionTokens.SweepDuration, easing = MotionTokens.EasingStandard)) }
            streaks.forEachIndexed { k, streak ->
                launch { streak.animateTo(1f, tween(SlamStreakLineDuration, k * SlamStreakLineStagger, MotionTokens.EasingEmphasizedAccelerate)) }
            }
            popIn()
        }
    }

    /** The card is leaving mid-slam: everything jumps to its end, the streaks gone (the prototype's `finalSlab`). */
    suspend fun finish() {
        listOf(top, meta, cta, firstSolve, peel, strike, ghostLabel, stamp, popAlpha, popScale).forEach { it.snapTo(1f) }
        (streaks + recoil + pulse).forEach { it.snapTo(0f) }
        ghostShown = true
        odometer.settled = true
    }

    /** The recoil's offset: down [RecapMotion.SlamRecoil], up [RecapMotion.SlamRecoilBack], back to rest. */
    fun recoilOffset(): Dp {
        val e = recoil.value
        val down = RecapMotion.SlamRecoil.value
        val back = RecapMotion.SlamRecoilBack.value
        return when {
            e <= 0f || e >= 1f -> 0f
            e < RecoilDownAt -> down * e / RecoilDownAt
            e < RecoilBackAt -> lerp(down, back, (e - RecoilDownAt) / (RecoilBackAt - RecoilDownAt))
            else -> lerp(back, 0f, (e - RecoilBackAt) / (1f - RecoilBackAt))
        }.dp
    }

    /** The replay pill's scale: the pulse while it runs, else the pop. */
    fun pillScale(): Float {
        val e = pulse.value
        if (e <= 0f || e >= 1f) return popScale.value
        return if (e < 0.5f) lerp(1f, MotionTokens.PulseScale, e * 2f) else lerp(MotionTokens.PulseScale, 1f, e * 2f - 1f)
    }

    private suspend fun climb(ms: Long) =
        odometer.roll(ms, up = true, RecapMotion.SlamClimb, SlamClimbStagger, MotionTokens.EasingEmphasizedDecelerate)

    private fun CoroutineScope.rise(value: Animatable<Float, AnimationVector1D>, delayMs: Int) =
        launch { value.animateTo(1f, tween(MotionTokens.EnterDuration, delayMs, MotionTokens.EasingEmphasizedDecelerate)) }

    /** The prototype's `ctaIn`: the pill blinks out and fades back in as it stamps, then pulses once. */
    private fun CoroutineScope.popIn() {
        launch {
            popAlpha.snapTo(0f)
            popAlpha.animateTo(1f, tween(MotionTokens.DurationShort, easing = MotionTokens.EasingLinear))
        }
        launch {
            popScale.snapTo(MotionTokens.StampFromScale)
            popScale.animateTo(1f, stampSpring)
        }
        launch {
            delay(ReplayPulseDelay.toLong())
            pulse.animateTo(1f, tween(MotionTokens.PulseDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
        }
    }

    private val stampSpring = spring<Float>(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness)
}

/** A [Slam] for [row] that plays once its card arrives, or jumps to its end once the card is [leaving]. */
@Composable
internal fun rememberSlam(row: RecapCard.Fastest.Row, instant: Boolean, leaving: Boolean): Slam {
    val slam = remember(row) { Slam(row.timeMs, row.averageMs, instant) }
    LaunchedEffect(slam, leaving) { if (leaving) slam.finish() else slam.play(GameStepArriveBase) }
    return slam
}

/** The impact's streaks: white lines that cross the card left to right, staggered top to bottom. */
internal fun DrawScope.drawSlamStreaks(slam: Slam) {
    val width = size.width
    slam.streaks.forEachIndexed { k, streak ->
        val run = streak.value
        if (run <= 0f || run >= 1f) return@forEachIndexed
        drawRoundRect(
            StreakColor,
            topLeft = Offset(width * (STREAK_FROM + STREAK_TRAVEL * run), size.height * STREAK_TOPS[k]),
            size = Size(width * STREAK_LENGTH, StreakHeight.toPx()),
            cornerRadius = CornerRadius(StreakRadius.toPx()),
        )
    }
}

// The prototype's slam values that motion.md has no token for, named after the prototype's variables.
private const val GameStepArriveBase = 140
private const val ClimbLead = 160
private const val SlamClimbStagger = 50
private const val SlamDropStagger = 30
private const val StrikeDelay = 340
private const val GhostLabelDelay = 420
private const val SlamStreakLineDuration = 360
private const val SlamStreakLineStagger = 40
private const val ReplayPulseDelay = 520

/** The recoil keyframes: down by 18% of the run, up by half, at rest by the end. */
private const val RecoilDownAt = 0.18f
private const val RecoilBackAt = 0.5f

/** A streak is half the card wide, starts 60% of a card left of it and travels 1.6 cards (`.streak`). */
private val STREAK_TOPS = floatArrayOf(0.28f, 0.52f, 0.76f)
private const val STREAK_FROM = -0.6f
private const val STREAK_TRAVEL = 1.6f
private const val STREAK_LENGTH = 0.5f
private val StreakHeight = 3.dp
private val StreakRadius = 2.dp
private val StreakColor = Color.White.copy(alpha = 0.45f)

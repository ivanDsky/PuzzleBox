package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.RollingDigits
import com.ivandsky.puzzlebox.designsystem.formatDuration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the slam stands: the timer climbing to the average, the average peeling off, or the fastest time landed. */
internal enum class SlamPhase { Climb, Peel, Landed }

/**
 * One game's slam: the timer climbs to the average, holds, the average peels off as a struck-through ghost, the fastest
 * time drops in with a recoil and the replay pill is stamped. A game with no average skips the middle: the timer rolls
 * straight to the time. With [instant] (reduced motion, a preview) every value starts at its end state, under one
 * [MotionTokens.ReducedFadeDuration] crossfade when [reduced]. The card reads the values in layer blocks, so a frame
 * redraws and never recomposes.
 */
internal class Slam(private val timeMs: Long, private val averageMs: Long?, private val instant: Boolean, private val reduced: Boolean) {
    var phase by mutableStateOf(if (instant || averageMs == null) SlamPhase.Landed else SlamPhase.Climb)
        private set

    /** The ghost average's lift into place and the strike line drawn through it, 0..1. */
    val ghost = Animatable(if (instant || averageMs == null) 1f else 0f)
    val strike = Animatable(if (instant) 1f else 0f)

    /** The fastest time's fall, 0 above to 1 landed; [impact] is its scale and [recoil] its bounce, dp. */
    val drop = Animatable(if (instant || averageMs == null) 1f else 0f)
    val impact = Animatable(1f)
    val recoil = Animatable(0f)

    /** The replay pill's stamp, 0..1, and the card's fade in under reduced motion. */
    val stamp = Animatable(if (instant) 1f else 0f)
    val reveal = Animatable(if (reduced) 0f else 1f)

    /** Columns of the average's odometer, so the hold starts once the last has stopped. */
    private val climbMs = RecapMotion.SlamClimb + RecapMotion.OdometerStagger * ((averageMs ?: timeMs).let(::columnsOf) - 1)

    suspend fun play() = coroutineScope {
        if (instant) {
            reveal.animateTo(1f, tween(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingLinear))
            return@coroutineScope
        }
        if (averageMs == null) {
            delay(climbMs.toLong())
            stamp.animateTo(1f, stampSpring)
            return@coroutineScope
        }
        delay((climbMs + RecapMotion.SlamHold).toLong())
        phase = SlamPhase.Peel
        launch { strike.animateTo(1f, tween(STRIKE_MS, easing = MotionTokens.EasingStandard)) }
        ghost.animateTo(1f, tween(RecapMotion.SlamPeel, easing = MotionTokens.EasingStandard))
        phase = SlamPhase.Landed
        drop.animateTo(1f, tween(RecapMotion.SlamDrop, easing = MotionTokens.EasingEmphasizedAccelerate))
        launch {
            impact.snapTo(IMPACT_FROM)
            impact.animateTo(1f, spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness))
        }
        launch {
            recoil.animateTo(RecapMotion.SlamRecoil.value, tween(RECOIL_MS))
            recoil.animateTo(RecapMotion.SlamRecoilBack.value, tween(RECOIL_MS))
            recoil.animateTo(0f, tween(RECOIL_MS))
        }
        delay(RecapMotion.SlamLandingPause.toLong())
        stamp.animateTo(1f, stampSpring)
    }

    private val stampSpring = spring<Float>(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness)

    private fun columnsOf(ms: Long): Int = formatDuration(ms).count { it.isDigit() }
}

/** A [Slam] that plays for [key] (the game and the card's entrance), after [startDelayMs] for the numbers to arrive. */
@Composable
internal fun rememberSlam(key: Any, timeMs: Long, averageMs: Long?, instant: Boolean, reduced: Boolean, startDelayMs: Int): Slam {
    val slam = remember(key) { Slam(timeMs, averageMs, instant, reduced) }
    LaunchedEffect(slam) {
        delay(startDelayMs.toLong())
        slam.play()
    }
    return slam
}

/**
 * [text] ("0:48", "1:02:10") as odometer columns with the colons fixed between them: each digit rolls over [durationMs]
 * with the shared odometer stagger and laps (ones laps twice, tens and hundreds once), the left column first. A screen
 * reader gets the settled time. With no [durationMs] it is the settled text, in the odometer's line height.
 */
@Composable
internal fun TimeOdometer(text: String, style: TextStyle, color: Color, durationMs: Int, modifier: Modifier = Modifier) {
    val digits = text.count { it.isDigit() }
    val colon = style.copy(lineHeight = style.fontSize * COLON_LINE)
    if (durationMs == 0) {
        Text(text, modifier.clearAndSetSemantics { contentDescription = text }, color, style = colon, softWrap = false, maxLines = 1)
        return
    }
    var column = 0
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        text.forEach { char ->
            if (char == ':') {
                Text(":", style = colon, color = color)
            } else {
                val place = digits - 1 - column
                RollingDigits(
                    value = (char - '0').toLong(),
                    style = style,
                    color = color,
                    delayMs = column * RecapMotion.OdometerStagger,
                    durationMs = durationMs,
                    staggerMs = 0,
                    laps = listOf(RecapMotion.OdometerLaps.getOrElse(place) { RecapMotion.OdometerLapsBeyond }),
                )
                column++
            }
        }
    }
}

/** The odometer's digit window is this many ems tall, and the colons share its line. */
private const val COLON_LINE = 1.1f
private const val STRIKE_MS = 340
private const val IMPACT_FROM = 1.12f
private const val RECOIL_MS = 60

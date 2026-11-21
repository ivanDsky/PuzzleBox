package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How far (0..1) a Solved entrance move has come: it starts [delayMs] after the card is shown and runs [durationMs] on
 * EmphasizedDecelerate. Reduced motion and a preview are at 1 at once. The deck re-creates the face on a replay, so the
 * move plays again. Read the state in a layer or draw block, so a frame never recomposes its reader.
 */
@Composable
internal fun rememberSolvedProgress(delayMs: Int, durationMs: Int = MotionTokens.DurationExpressive): State<Float> {
    val instant = rememberReducedMotion() || entranceSettled()
    val progress = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(progress, instant) {
        if (instant) progress.snapTo(1f) else progress.animateTo(1f, tween(durationMs, delayMillis = delayMs, easing = MotionTokens.EasingEmphasizedDecelerate))
    }
    return progress.asState()
}

/** [rememberSolvedProgress]'s value, read where it is called. */
@Composable
internal fun rememberSolvedMove(delayMs: Int, durationMs: Int = MotionTokens.DurationExpressive): Float = rememberSolvedProgress(delayMs, durationMs).value

/** An Enter: a fade and a [MotionTokens.EnterRise] rise over [MotionTokens.EnterDuration], starting [delayMs] after the card is shown. */
@Composable
internal fun Modifier.solvedEnter(delayMs: Int): Modifier {
    val enter = rememberSolvedProgress(delayMs, MotionTokens.EnterDuration)
    val rise = with(LocalDensity.current) { MotionTokens.EnterRise.toPx() }
    return graphicsLayer {
        alpha = enter.value
        translationY = (1f - enter.value) * rise
    }
}

/**
 * A count's landing (Stamp): this modifier scales it from [MotionTokens.StampFromScale] to 1 on the Stamp spring about its
 * bottom-right corner while it fades in over [MotionTokens.StampFadeDuration], both starting [delayMs] after the card is
 * shown. Reduced motion and a preview are at rest at once (the page's one fade stands in).
 */
@Composable
internal fun Modifier.solvedStamp(delayMs: Int): Modifier {
    val instant = rememberReducedMotion() || entranceSettled()
    val scale = remember { Animatable(if (instant) 1f else MotionTokens.StampFromScale) }
    val fade = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(scale, instant) {
        if (instant) {
            scale.snapTo(1f)
            fade.snapTo(1f)
        } else {
            delay(delayMs.toLong())
            launch { fade.animateTo(1f, tween(MotionTokens.StampFadeDuration, easing = LinearEasing)) }
            scale.animateTo(1f, spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness))
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = fade.value
        transformOrigin = TransformOrigin(1f, 1f)
    }
}

/** The card's own 120ms fade in when motion is reduced (every other part is already at its final value); 1 otherwise. */
@Composable
internal fun rememberReducedFade(): Float {
    val fade = remember { Animatable(0f) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(reduced) { if (reduced) fade.animateTo(1f, tween(MotionTokens.ReducedFadeDuration)) else fade.snapTo(1f) }
    return if (reduced) fade.value else 1f
}

/** One band as the tide sees it: its [solves], and its [bottom] edge above the sheet's bottom and its [height], as fractions of the sheet. */
internal data class TideBand(val solves: Int, val bottom: Float, val height: Float) {
    /** How far above the sheet's bottom the band's top stands: the level that has cleared it. */
    val reach: Float get() = bottom + height
}

/** The bands stacked top down with these [heights] (any unit), as the tide sees them; none on a sheet with no height. */
internal fun tideBands(solves: List<Int>, heights: List<Float>): List<TideBand> {
    val sheet = heights.sum()
    if (sheet <= 0f) return emptyList()
    var top = 0f
    return solves.indices.map { index ->
        top += heights[index]
        TideBand(solves[index], bottom = 1f - top / sheet, height = heights[index] / sheet)
    }
}

/**
 * The Solved card's tide: one linear [progress] (0..1) the level rises on, eased by [MotionTokens.EasingEase], and the
 * [bands] it fills, published by the sheet once it knows their heights. Read [progress] in a draw or layer block, or
 * through [shownTotal] in a derived state, so a frame never recomposes the page.
 */
@Stable
internal class SolvedTide(private val total: Int, start: Float) {
    val progress = Animatable(start)
    var bands: List<TideBand> by mutableStateOf(emptyList())

    /** The number the hero shows: the solves under the level, the total once the tide is in (so a settled page never needs [bands]). */
    fun shownTotal(): Int {
        val lin = progress.value
        return if (lin >= 1f) total else tideTotal(MotionTokens.EasingEase.transform(lin), bands)
    }
}

/**
 * The tide of [card]'s page: it rises over [TideRiseMs] on a linear driver from [RecapMotion.CountHeroDelay] after the
 * card is shown. Reduced motion and a preview are full at once. The page creates it once and hands it down.
 */
@Composable
internal fun rememberSolvedTide(card: RecapCard.Solved): SolvedTide {
    val instant = rememberReducedMotion() || entranceSettled()
    val tide = remember(card) { SolvedTide(card.total, if (instant) 1f else 0f) }
    LaunchedEffect(tide, instant) {
        if (instant) {
            tide.progress.snapTo(1f)
        } else {
            tide.progress.animateTo(1f, tween(TideRiseMs, delayMillis = RecapMotion.CountHeroDelay, easing = LinearEasing))
        }
    }
    return tide
}

/** The solves under a [level] (0..1 of the sheet, from its bottom): each band's count times the share of it under the level, rounded. */
internal fun tideTotal(level: Float, bands: List<TideBand>): Int {
    var sum = 0f
    for (index in bands.indices) {
        val band = bands[index]
        if (band.height > 0f) sum += band.solves * ((level - band.bottom) / band.height).coerceIn(0f, 1f)
    }
    return sum.roundToInt()
}

/** When the level clears a band whose top stands [reach] (0..1 of the sheet) above the bottom, ms after the card is shown. */
internal fun tideLandMs(reach: Float): Int =
    RecapMotion.CountHeroDelay + (TideRiseMs * easeInverse((reach - LandEpsilon).coerceIn(0f, 1f))).roundToInt()

/** The linear progress at which [MotionTokens.EasingEase] reaches [level], by bisection. */
internal fun easeInverse(level: Float): Float {
    var low = 0f
    var high = 1f
    repeat(BisectionSteps) {
        val mid = (low + high) / 2f
        if (MotionTokens.EasingEase.transform(mid) < level) low = mid else high = mid
    }
    return high
}

/** The surface's wave height at level [p]: [TideWave] grown over the first sixth of the rise and calmed to 0 at the top. */
internal fun tideWave(p: Float): Dp = TideWave * ((1f - p) * min(1f, TideWaveSwell * p))

/** When the stripe comes in: [StripeAfterTideMs] after the tide is in, for any number of games. */
internal fun stripeStartMs(): Int = RecapMotion.CountHeroDelay + TideRiseMs + StripeAfterTideMs

/**
 * Shows only what is under [tide]'s wavy surface, so the bands fill from the bottom like liquid. One path per size,
 * rewound each frame; the level is read here alone (a frame redraws, never recomposes). Nothing shows before the rise,
 * and a settled tide draws with no clip.
 */
internal fun Modifier.tideClip(tide: SolvedTide): Modifier = drawWithCache {
    val surface = Path()
    val wavelength = TideWavelength.toPx()
    onDrawWithContent {
        val lin = tide.progress.value
        if (lin >= 1f) return@onDrawWithContent drawContent()
        val p = MotionTokens.EasingEase.transform(lin)
        val amplitude = tideWave(p).toPx()
        val phase = (RecapMotion.CountHeroDelay + lin * TideRiseMs) / TideWaveSpeedMs
        val top = size.height * (1f - p)
        surface.rewind()
        surface.moveTo(0f, size.height)
        for (i in 0..TideSegments) {
            val x = size.width * i / TideSegments
            surface.lineTo(x, top + amplitude * sin(2f * PI.toFloat() * x / wavelength + phase))
        }
        surface.lineTo(size.width, size.height)
        surface.close()
        clipPath(surface) { this@onDrawWithContent.drawContent() }
    }
}

/** The glass the tide fills: [ink] at [TideTrackAlpha], fading in with the page's first Enter, so the empty column shows before the rise. */
@Composable
internal fun Modifier.tideTrack(ink: Color): Modifier {
    val enter = rememberSolvedProgress(0, MotionTokens.EnterDuration)
    return graphicsLayer { alpha = enter.value }.background(ink.copy(alpha = TideTrackAlpha))
}

/** The stripe's count starts this long after the stripe does, and its second line comes in [StripeSubDelayMs] after it. */
internal const val StripeCountDelayMs = 100
internal const val StripeSubDelayMs = 260

private const val TideRiseMs = 1700
private val TideWave = 10.dp

/** The wave's envelope `(1 − p) × min(1, 6p)`: it grows over the first sixth and calms to 0 at the top, 8.3dp at its peak. */
private const val TideWaveSwell = 6f
private val TideWavelength = 160.dp

/** The wave's phase is the elapsed ms over this, in radians: a crest passes in about 1.07s. */
private const val TideWaveSpeedMs = 170f
private const val TideSegments = 24
private const val TideTrackAlpha = 0.10f

/** The stripe comes in as the top band's Stamp settles: 200 + 1700 + 230 = 2130ms. */
private const val StripeAfterTideMs = 230

/** A band lands a hair before the level reaches its top, so the top band lands inside the rise. */
private const val LandEpsilon = 0.0001f
private const val BisectionSteps = 20

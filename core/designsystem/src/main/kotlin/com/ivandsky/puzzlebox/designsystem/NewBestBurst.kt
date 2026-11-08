package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The new-best burst's constants: particle count, length, reach and size. */
internal object NewBestBurstDefaults {
    const val ParticleCount = 18
    const val DurationMs = 480

    /** Where a particle ends, as a fraction of the radius; none lands closer than this. */
    const val MinTravel = 0.55f

    /** A particle's size as a fraction of [MaxParticle]; none is smaller than this. */
    const val MinSize = 0.5f

    val Radius: Dp = 96.dp
    val MaxParticle: Dp = 8.dp
}

/**
 * One burst particle: it flies out along [angleRad] to [distance] (a fraction of the burst
 * radius) and is [size] (a fraction of the largest dot) wide; [colorIndex] picks its color.
 */
internal data class BurstParticle(val angleRad: Float, val distance: Float, val size: Float, val colorIndex: Int)

/** The particle field for [seed]: [count] particles spread round the circle, each jittered. Pure, so tests can pin it. */
internal fun burstParticles(count: Int = NewBestBurstDefaults.ParticleCount, seed: Long): List<BurstParticle> {
    val random = Random(seed)
    val step = (2 * Math.PI).toFloat() / count
    return List(count) { i ->
        BurstParticle(
            angleRad = (i + random.nextFloat() * 0.8f) * step,
            distance = NewBestBurstDefaults.MinTravel + random.nextFloat() * (1f - NewBestBurstDefaults.MinTravel),
            size = NewBestBurstDefaults.MinSize + random.nextFloat() * (1f - NewBestBurstDefaults.MinSize),
            colorIndex = i % COLOR_COUNT,
        )
    }
}

private const val COLOR_COUNT = 3

/**
 * Fills its parent and, once [trigger] turns true, flies [NewBestBurstDefaults.ParticleCount]
 * dots out from [origin] (a fraction of the parent's size) over [NewBestBurstDefaults.DurationMs]
 * on a decelerate curve, fading as they go. [colors] are used in turn (accent, gold, primary).
 * Plays once per trigger. Reduced motion and previews draw nothing. Draw-phase only.
 */
@Composable
fun NewBestBurst(
    trigger: Boolean,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    origin: Offset = Offset(0.5f, 0.5f),
    seed: Long = 0L,
) {
    val reduced = rememberReducedMotion() || LocalInspectionMode.current
    val progress = remember { Animatable(0f) }
    val particles = remember(seed) { burstParticles(seed = seed) }
    LaunchedEffect(trigger, reduced) {
        if (!trigger || reduced) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(NewBestBurstDefaults.DurationMs, easing = MotionTokens.EasingEmphasizedDecelerate))
    }
    if (reduced || colors.isEmpty()) return
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas
        val center = Offset(size.width * origin.x, size.height * origin.y)
        val radius = NewBestBurstDefaults.Radius.toPx()
        val dot = NewBestBurstDefaults.MaxParticle.toPx()
        for (particle in particles) {
            val reach = radius * particle.distance * t
            drawCircle(
                color = colors[particle.colorIndex % colors.size].copy(alpha = 1f - t),
                radius = dot * particle.size / 2f,
                center = center + Offset(cos(particle.angleRad) * reach, sin(particle.angleRad) * reach),
            )
        }
    }
}

// Preview — Studio tooling only. The burst's end state: a preview never plays it, so the
// pane shows the empty host it draws over.
@PuzzleBoxPreviews
@Composable
internal fun NewBestBurstPreview() {
    PuzzleBoxTheme {
        Box(Modifier.size(160.dp).background(MaterialTheme.colorScheme.background)) {
            NewBestBurst(
                trigger = true,
                colors = listOf(AccentSlot.Violet.colors().accent, MaterialTheme.colorScheme.tertiary),
            )
        }
    }
}

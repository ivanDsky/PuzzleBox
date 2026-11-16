package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The celebration burst's constants: piece count, length, reach, fall and size. */
internal object SolveCelebrationBurstDefaults {
    const val PieceCount = 44
    const val DurationMs = 600

    /** Where a piece ends, as a fraction of the radius; none lands closer than this. */
    const val MinTravel = 0.4f

    /** A piece's size as a fraction of [MaxPiece]; none is smaller than this. */
    const val MinSize = 0.5f

    /** How far every piece has fallen by the end, as a fraction of the radius. */
    const val Fall = 0.16f

    /** The pieces are solid up to this point of the burst and fade out over the rest. */
    const val FadeFrom = 0.5f

    /** The ring's reach as a fraction of the pieces'. */
    const val RingReach = 0.7f

    /** Most turns a strip makes on its way out, either way. */
    const val MaxTurns = 1.5f

    val Radius: Dp = 176.dp
    val MaxPiece: Dp = 12.dp
    val RingWidth: Dp = 3.dp
}

/**
 * One piece of confetti: it flies out along [angleRad] to [distance] (a fraction of the burst radius) and is [size]
 * (a fraction of the largest piece) wide; [colorIndex] picks its colour. A [round] one is a dot, any other a strip
 * that makes [turns] turns on the way.
 */
internal data class ConfettiPiece(
    val angleRad: Float,
    val distance: Float,
    val size: Float,
    val colorIndex: Int,
    val round: Boolean,
    val turns: Float,
)

/** The confetti for [seed]: [count] pieces spread round the circle, each jittered. Pure, so a frame can be pinned. */
internal fun confettiPieces(seed: Long, count: Int = SolveCelebrationBurstDefaults.PieceCount): List<ConfettiPiece> {
    val random = Random(seed)
    val step = (2 * Math.PI).toFloat() / count
    return List(count) { i ->
        ConfettiPiece(
            angleRad = (i + random.nextFloat() * 0.8f) * step,
            distance = SolveCelebrationBurstDefaults.MinTravel +
                random.nextFloat() * (1f - SolveCelebrationBurstDefaults.MinTravel),
            size = SolveCelebrationBurstDefaults.MinSize + random.nextFloat() * (1f - SolveCelebrationBurstDefaults.MinSize),
            colorIndex = i,
            round = i % 3 == 0,
            turns = (random.nextFloat() * 2f - 1f) * SolveCelebrationBurstDefaults.MaxTurns,
        )
    }
}

/**
 * One burst of the solve celebration (L2), played once from its first frame and then gone: confetti in [colors]
 * thrown from the centre behind the stamp. [index] numbers the bursts of one celebration and seeds the confetti, so
 * a second burst is not the first again. The caller leaves it out under reduced motion.
 */
@Composable
internal fun SolveCelebrationBurst(index: Int, colors: List<Color>, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(SolveCelebrationBurstDefaults.DurationMs, easing = LinearEasing))
        playing = false
    }
    if (playing) {
        SolveCelebrationBurstFrame(
            progress = { progress.value },
            colors = colors,
            seed = index.toLong(),
            modifier = modifier.testTag("solve.celebration_burst.$index"),
        )
    }
}

/**
 * The burst at [progress] (0..1, linear time): a ring and the confetti of [seed] fly out from the centre on the
 * emphasized-decelerate curve, falling a little, and fade over the second half. Nothing is drawn at either end.
 */
@Composable
internal fun SolveCelebrationBurstFrame(
    progress: () -> Float,
    colors: List<Color>,
    seed: Long,
    modifier: Modifier = Modifier,
) {
    val pieces = remember(seed) { confettiPieces(seed) }
    Canvas(modifier.fillMaxSize()) {
        val time = progress()
        if (time <= 0f || time >= 1f || colors.isEmpty()) return@Canvas
        val radius = SolveCelebrationBurstDefaults.Radius.toPx()
        val reach = radius * MotionTokens.EasingEmphasizedDecelerate.transform(time)
        val fade = SolveCelebrationBurstDefaults.FadeFrom
        val alpha = if (time < fade) 1f else (1f - time) / (1f - fade)
        drawCircle(
            color = colors.first(),
            radius = reach * SolveCelebrationBurstDefaults.RingReach,
            alpha = alpha * (1f - time),
            style = Stroke(SolveCelebrationBurstDefaults.RingWidth.toPx()),
        )
        val fall = radius * SolveCelebrationBurstDefaults.Fall * time * time
        pieces.forEach { piece ->
            val at = center + Offset(
                x = cos(piece.angleRad) * reach * piece.distance,
                y = sin(piece.angleRad) * reach * piece.distance + fall,
            )
            drawPiece(piece, at, colors[piece.colorIndex % colors.size], alpha, time)
        }
    }
}

private fun DrawScope.drawPiece(piece: ConfettiPiece, at: Offset, color: Color, alpha: Float, time: Float) {
    val width = SolveCelebrationBurstDefaults.MaxPiece.toPx() * piece.size
    if (piece.round) {
        drawCircle(color, radius = width / 2f, center = at, alpha = alpha)
        return
    }
    val strip = Size(width, width / 2f)
    rotate(degrees = Math.toDegrees(piece.angleRad.toDouble()).toFloat() + piece.turns * FULL_TURN * time, pivot = at) {
        drawRect(color, topLeft = at - Offset(strip.width / 2f, strip.height / 2f), size = strip, alpha = alpha)
    }
}

private const val FULL_TURN = 360f

/** The burst's colours for [brand]: its accent and container, then the theme's tertiary and primary. */
@Composable
internal fun solveCelebrationBurstColors(brand: GameBrand): List<Color> {
    val accent = brand.accent.colors()
    return listOf(accent.accent, accent.container, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary)
}

// Preview: the burst half-way out, over a plain surface. A fixed progress, so the pane is static (A8).
@PuzzleBoxPreviews
@Composable
internal fun SolveCelebrationBurstPreview() {
    PuzzleBoxTheme {
        Box(Modifier.size(360.dp).background(MaterialTheme.colorScheme.surface)) {
            SolveCelebrationBurstFrame(
                progress = { 0.5f },
                colors = solveCelebrationBurstColors(GameBrand.Neutral.copy(accent = AccentSlot.Teal)),
                seed = 0L,
            )
        }
    }
}

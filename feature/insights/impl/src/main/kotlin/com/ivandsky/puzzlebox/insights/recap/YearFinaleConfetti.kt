package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Sixteen pieces fly out once, [RecapMotion.ConfettiDelay] ms after the last tile starts; reduced motion and previews draw none. */
@Composable
internal fun Confetti(ink: Color, plate: Color) {
    if (rememberReducedMotion() || entranceSettled()) return
    val pieces = remember {
        val random = Random(ConfettiSeed)
        List(RecapMotion.ConfettiPieces) { index ->
            val angle = Math.toRadians(-ConfettiSpreadStart - random.nextDouble() * ConfettiSpread)
            val speed = RecapMotion.ConfettiSpeedMin + random.nextFloat() * (RecapMotion.ConfettiSpeedMax - RecapMotion.ConfettiSpeedMin)
            Piece(cos(angle).toFloat() * speed, sin(angle).toFloat() * speed, index)
        }
    }
    var elapsedMs by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(Unit) {
        delay((RecapMotion.YearTileStart + (TileCount - 1) * RecapMotion.YearTileStep + RecapMotion.ConfettiDelay).toLong())
        val start = withFrameNanos { it }
        while (elapsedMs < RecapMotion.ConfettiFade) elapsedMs = (withFrameNanos { it } - start) / NanosPerMs
    }
    if (elapsedMs < 0 || elapsedMs >= RecapMotion.ConfettiFade) return
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize()) {
        val t = elapsedMs / 1000f
        val dp = density.density
        val origin = Offset(size.width / 2f, size.height * ConfettiOriginY)
        val alpha = 1f - elapsedMs.toFloat() / RecapMotion.ConfettiFade
        for (piece in pieces) {
            val x = origin.x + piece.vx * dp * t
            val y = origin.y + piece.vy * dp * t + RecapMotion.ConfettiGravity * dp * t * t / 2f
            val color = if (piece.index % 2 == 0) ink else plate
            drawCircle(color.copy(alpha = alpha * (if (piece.index % 3 == 0) 0.6f else 1f)), radius = ConfettiRadius.toPx(), center = Offset(x, y))
        }
    }
}

private data class Piece(val vx: Float, val vy: Float, val index: Int)


private const val TileCount = 12
private val ConfettiRadius = 4.dp
private const val ConfettiSeed = 2026
private const val ConfettiSpreadStart = 30.0
private const val ConfettiSpread = 120.0
private const val ConfettiOriginY = 0.3f
private const val NanosPerMs = 1_000_000L

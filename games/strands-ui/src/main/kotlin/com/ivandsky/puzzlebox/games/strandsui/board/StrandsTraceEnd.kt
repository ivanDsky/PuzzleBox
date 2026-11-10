package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strandsui.TraceEnd
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.GridGeometry

/** The submitted word's [path] at one instant of its end: [offsetPx] sideways, [alpha], and the tokens' [scale]. */
internal class TraceEndFrame(
    val path: List<Coord>,
    val error: Boolean,
    val offsetPx: Float,
    val alpha: Float,
    val scale: Float,
) {
    fun fill(colors: BoardColors): Color = if (error) colors.strandsPathError else TraceOverlaySpec.fill(colors)

    fun lip(colors: BoardColors): Color =
        if (error) lerp(colors.strandsPathError, Color.Black, ErrorLipShade) else TraceOverlaySpec.lip(colors)

    fun letter(colors: BoardColors): Color = if (error) colors.strandsPathErrorInk else TraceOverlaySpec.letter(colors)

    fun connector(colors: BoardColors): Color = if (error) colors.strandsPathError else colors.strandsTraceConnector
}

internal fun TraceEndFrame?.at(coord: Coord): TraceEndFrame? = this?.takeIf { coord in path }

/** How far the red lip sits below the red fill, toward black. */
private const val ErrorLipShade = 0.35f

/**
 * Plays [end] on the frame clock: null before it, after it, and from the first touch-down,
 * which ends it at once. Read in the draw phase, so a frame redraws without recomposing.
 */
@Composable
internal fun rememberTraceEndFrame(end: TraceEnd?, reduceMotion: Boolean, touchSeq: IntState): State<TraceEndFrame?> {
    val amplitude = with(LocalDensity.current) { MotionTokens.ErrorShake.toPx() }
    val frame = remember(end, reduceMotion) { mutableStateOf(end?.let { frameOf(it, 0, reduceMotion, amplitude) }) }
    // Unobserved: a touch-down must not recompose the board, only end the motion below.
    val bornTouch = remember(end, reduceMotion) { Snapshot.withoutReadObservation { touchSeq.intValue } }
    LaunchedEffect(end, reduceMotion) {
        if (end == null) {
            frame.value = null
            return@LaunchedEffect
        }
        val start = withFrameMillis { it }
        var elapsed = 0
        while (touchSeq.intValue == bornTouch) {
            val now = frameOf(end, elapsed, reduceMotion, amplitude) ?: break
            frame.value = now
            elapsed = (withFrameMillis { it } - start).toInt()
        }
        frame.value = null
    }
    return frame
}

private fun frameOf(end: TraceEnd, elapsedMs: Int, reduceMotion: Boolean, amplitude: Float): TraceEndFrame? =
    TraceEndTimeline.look(end.kind, elapsedMs, reduceMotion, end.pulse)?.let {
        TraceEndFrame(end.path, it.error, it.shake * amplitude, it.alpha, it.scale)
    }

/**
 * The submitted word's connector while it plays out, drawn after the board's own links and
 * under the letters: a round-capped polyline through the path's centres, red or neutral, shaken
 * and faded with its tokens. Its own layer, so [StrandsConnectors]' trace motion never has to know.
 * A dissolving token is translucent and shows what is under it, so from the first faded frame
 * the line is cut out of the path's tokens (as they are scaled), never seen through them.
 */
@Composable
internal fun StrandsTraceEndConnector(rows: Int, cols: Int, frame: () -> TraceEndFrame?, colors: BoardColors) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val geometry = GridGeometry(rows, cols, size.width, size.height)
                val width = connectorWidth(LinkKind.Trace, geometry.pitch)
                var builtFor: List<Coord>? = null
                var tokensFor: List<Coord>? = null
                var tokensScale = Float.NaN
                var stroke = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                var strokeScale = 1f
                val line = Path()
                val tokens = Path()
                val half = geometry.pitch * StrandsBoardSpec.TokenFraction / 2
                onDrawBehind {
                    val now = frame() ?: return@onDrawBehind
                    if (builtFor !== now.path) {
                        builtFor = now.path
                        line.reset()
                        now.path.forEachIndexed { index, coord ->
                            val center = geometry.centerOf(GridCell(coord.row, coord.col))
                            if (index == 0) line.moveTo(center.x, center.y) else line.lineTo(center.x, center.y)
                        }
                    }
                    if (strokeScale != now.scale) {
                        strokeScale = now.scale
                        stroke = Stroke(width = width * now.scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    }
                    val color = now.connector(colors)
                    translate(now.offsetPx, 0f) {
                        val faded = color.copy(alpha = color.alpha * now.alpha)
                        if (now.alpha >= 1f) {
                            drawPath(line, faded, style = stroke)
                        } else {
                            if (tokensFor !== now.path || tokensScale != now.scale) {
                                tokensFor = now.path
                                tokensScale = now.scale
                                tokens.reset()
                                now.path.forEach { coord ->
                                    tokens.addToken(geometry.centerOf(GridCell(coord.row, coord.col)), half, now.scale)
                                }
                            }
                            clipPath(tokens, ClipOp.Difference) { drawPath(line, faded, style = stroke) }
                        }
                    }
                }
            },
    )
}

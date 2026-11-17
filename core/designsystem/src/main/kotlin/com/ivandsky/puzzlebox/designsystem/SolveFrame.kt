package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A [SolveMomentState] tied to this composition. Reduced motion (and the preview inspector, which never runs effects
 * past the first frame) plays straight to the end: the closed accent frame, with no band, pop or comet.
 */
@Composable
fun rememberSolveMomentState(): SolveMomentState {
    val state = remember { SolveMomentState() }
    val instant = rememberReducedMotion() || entranceSettled()
    SideEffect { state.instant = instant }
    return state
}

/**
 * The solve moment B around a board (M12-10), as [state] plays: the SolveSweep band crosses the board, clipped to
 * [shape]; as it leaves, the board with everything on it pops to CelebrationMotion.BoardPopScale and springs back;
 * meanwhile a line of [color] at MotionTokens.SweepOutlineWidth laps the board's outline once, clockwise from the left
 * end of its top edge, led by a thicker round-capped comet that fades as the lap closes. The closed frame is
 * SolveSweep's end state. Nothing here clips, so the pop and the comet reach a little past the board: keep 6 dp clear
 * around it. Layer and draw phase only: playing never recomposes the board.
 */
fun Modifier.solveFrame(state: SolveMomentState, color: Color, shape: Shape): Modifier =
    graphicsLayer {
        val pop = state.popScale
        scaleX = pop
        scaleY = pop
    }.drawWithCache {
        val width = MotionTokens.SweepOutlineWidth.toPx()
        val board = shape.createOutline(size, layoutDirection, this)
        // Inset by half the stroke so the closed frame stays inside the board bounds, as SolveSweep's outline does.
        val inset = shape.createOutline(Size(size.width - width, size.height - width), layoutDirection, this)
        // Square ends fill the corner the lap starts from, so closing the frame changes nothing.
        val line = Stroke(width, cap = StrokeCap.Square)
        val comet = Stroke(CelebrationMotion.FrameCometWidth.toPx(), cap = StrokeCap.Round)
        val lap by lazy(LazyThreadSafetyMode.NONE) { FrameLap(inset, width) }
        onDrawWithContent {
            drawContent()
            if (!state.playing) return@onDrawWithContent
            val ms = state.ms
            drawSolveSweepBand((ms / MotionTokens.SweepDuration).coerceAtMost(1f), color, board)
            val drawn = solveFrameLap(ms)
            if (drawn >= 1f) {
                translate(width / 2f, width / 2f) { drawOutline(inset, color, style = line) }
            } else if (drawn > 0f) {
                drawPath(lap.line(drawn), color, style = line)
                drawPath(lap.comet(drawn), color, alpha = solveCometAlpha(drawn), style = comet)
            }
        }
    }

/**
 * [outline], set [width] / 2 in from the board's corner, as one lap that can be cut to length: clockwise from the left
 * end of its top edge, whichever way and from wherever the shape's own path runs (a rectangle's starts at its corner;
 * Zip's rounded outline runs anticlockwise from its left edge).
 */
private class FrameLap(outline: Outline, width: Float) {
    private val measure = PathMeasure()
    private val length: Float
    private val clockwise: Boolean
    private val line = Path()
    private val comet = Path()

    init {
        val path = Path().apply {
            addOutline(outline)
            translate(Offset(width / 2f, width / 2f))
        }
        measure.setPath(path, forceClosed = false)
        val whole = measure.length
        // A point every stroke width finds the top edge's left end closely enough; the turn's sign gives the direction.
        val count = (whole / width).toInt().coerceAtLeast(MIN_POINTS)
        val points = List(count) { measure.getPosition(whole * it / count) }
        val top = points.minOf { it.y }
        val start = points.indices.filter { points[it].y <= top + EDGE_SLOP_PX }.minBy { points[it].x }
        var turn = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % count]
            turn += a.x * b.y - b.x * a.y
        }
        clockwise = turn > 0f

        val from = whole * start / count
        val lap = Path()
        measure.getSegment(from, whole, lap, startWithMoveTo = true)
        measure.getSegment(0f, from, lap, startWithMoveTo = false)
        measure.setPath(lap, forceClosed = false)
        length = measure.length
    }

    /** The first [drawn] of the lap. */
    fun line(drawn: Float): Path = cut(0f, drawn, line)

    /** The comet behind the line's head when [drawn] of the lap is drawn; never longer than the line. */
    fun comet(drawn: Float): Path = cut((drawn - CelebrationMotion.FrameCometFraction).coerceAtLeast(0f), drawn, comet)

    /** The lap from [from] to [to] of the way round, clockwise, into [into]; an anticlockwise path is cut from its far end. */
    private fun cut(from: Float, to: Float, into: Path): Path {
        into.rewind()
        if (clockwise) {
            measure.getSegment(from * length, to * length, into, startWithMoveTo = true)
        } else {
            measure.getSegment((1f - to) * length, (1f - from) * length, into, startWithMoveTo = true)
        }
        return into
    }

    private companion object {
        const val MIN_POINTS = 16
        const val EDGE_SLOP_PX = 0.1f
    }
}

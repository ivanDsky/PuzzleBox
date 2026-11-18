package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.DragTraceHooks
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The trace, hoisted to ONE board-level layer (ticket 28, the user's layer-level
 * subtraction): one fat rounded stroke along the path, drawn UNDER all cell content as
 * the [BoardGrid] underlay, so each dot's surface hole punches the pipe globally. A head
 * disc rides the line's end. In the live game [finger] (board px, read in the draw phase)
 * leans the end toward cells [hooks] accepts, and [animate] turns on grow-in, retract and
 * the tip's return home ([MotionTokens.Trace]); reduced motion keeps only the tip. The line is
 * one stroke, with no lip.
 */
@Composable
fun ZipPipes(
    model: ZipRendererModel,
    finger: () -> Offset? = { null },
    hooks: DragTraceHooks? = null,
    animate: Boolean = false,
) {
    val board = LocalBoardColors.current
    val scope = rememberCoroutineScope()
    val motion = remember { ZipLineMotion(scope) }
    val path = remember(model.path, model.n) { model.path.map { GridCell(it / model.n, it % model.n) } }
    Canvas(Modifier.fillMaxSize()) {
        with(motion) {
            drawTrace(
                path, finger(), hooks, model.n, board.pathTrace,
                ZipBoardSpec.pathTraceWidthFraction, animate,
            )
        }
    }
}

/**
 * Ticket 28 r5: the hint arrow — a plain arrowhead glyph CENTERED on the auto-placed
 * segment's head cell, rotated to the segment's direction, sized from the cell in the
 * number labels' size language, in the trace role's color. Nothing else is drawn — the
 * cap-wing chevron (stray pieces past the pipe) is deleted. Labeled heads are skipped:
 * their dot ring already marks them instead. The screen hangs it ABOVE the board
 * (sized to it); [alpha] carries the ~2s fade (reduced motion removes the arrow
 * instantly).
 */
@Composable
fun HintArrowHead(arrow: HintArrow, model: ZipRendererModel, alpha: Float, modifier: Modifier = Modifier) {
    val n = model.n
    // A labeled head already announces itself — its dot ring marks it, and
    // a blue glyph over the blue ring/number reads as mush
    // (user finding, board-review round 2). Plain heads get the arrow.
    if (model.cells[arrow.headIdx].label != null) return
    val board = LocalBoardColors.current
    Canvas(modifier.graphicsLayer { this.alpha = alpha }.testTag("play.hint_arrow")) {
        val headCol = arrow.headIdx % n
        val headRow = arrow.headIdx / n
        val dx = headCol - arrow.tailIdx % n
        val dy = headRow - arrow.tailIdx / n
        if (dx == 0 && dy == 0) return@Canvas // degenerate first placement: no direction
        val pitchX = size.width / n
        val pitchY = size.height / n
        val center = Offset((headCol + 0.5f) * pitchX, (headRow + 0.5f) * pitchY)
        // → glyph (user round 5: "like ->, not >", boardSurface = shader-subtract look,
        // every extent inside the pipe stroke so nothing hangs outside the line).
        val shaftHalf = pitchX * ZipBoardSpec.hintArrowShaftHalfThicknessFraction
        val headHalf = pitchX * ZipBoardSpec.hintArrowHeadHalfWidthFraction
        val angle = atan2(dy.toFloat(), dx.toFloat())
        val cosA = cos(angle)
        val sinA = sin(angle)
        fun rotated(offset: Offset) = Offset(
            center.x + offset.x * cosA - offset.y * sinA,
            center.y + offset.x * sinA + offset.y * cosA,
        )
        val shaftBack = -pitchX * ZipBoardSpec.hintArrowShaftBackFraction
        val headBase = pitchX * ZipBoardSpec.hintArrowShaftFrontFraction
        val tip = pitchX * ZipBoardSpec.hintArrowTipFraction
        // ONE silhouette (user round 6: "broken a little bit" — the old two-subpath fill
        // left an anti-aliased sliver on the shared shaft/head edge).
        val p1 = rotated(Offset(shaftBack, -shaftHalf))
        val p2 = rotated(Offset(headBase, -shaftHalf))
        val p3 = rotated(Offset(headBase, -headHalf))
        val p4 = rotated(Offset(tip, 0f))
        val p5 = rotated(Offset(headBase, headHalf))
        val p6 = rotated(Offset(headBase, shaftHalf))
        val p7 = rotated(Offset(shaftBack, shaftHalf))
        val glyph = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            lineTo(p4.x, p4.y)
            lineTo(p5.x, p5.y)
            lineTo(p6.x, p6.y)
            lineTo(p7.x, p7.y)
            close()
        }
        drawPath(glyph, color = board.boardSurface, style = Fill)
    }
}

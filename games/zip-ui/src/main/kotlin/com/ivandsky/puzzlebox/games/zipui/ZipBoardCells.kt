package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.DragTraceHooks
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val cellCorner = RoundedCornerShape(10.dp)

/**
 * One cell: LinkedIn-style waypoint dots with the bold label INSIDE the dot, the hint
 * ring, and the transient rejection pulse. The trace is NOT drawn here any more — it
 * lives in the board-level [ZipPipes] layer (ticket 28) under every cell's content, so
 * each dot's surface hole punches the pipe globally instead of per-cell caps clipping
 * into neighbor dots. Sizes come from [ZipBoardSpec] fractions of the cell. Resting by
 * contract (A8): the next-target flash and the waypoint pop when the trace reaches a dot
 * animate only in the live game — the play screen passes [animated] (false under reduced
 * motion); previews and read-only boards stay static.
 */
@Composable
fun ZipCellDecor(
    model: ZipRendererModel,
    row: Int,
    col: Int,
    modifier: Modifier = Modifier,
    animated: Boolean = false,
) {
    val idx = row * model.n + col
    val dotFraction = ZipBoardSpec.dotSizeFraction(model.n)
    val cell = model.cells[idx]
    val inPath = cell.isOnPath
    val isHint = model.hintHighlight == idx
    val isRejected = model.rejection?.cellIdx == idx
    val board = LocalBoardColors.current
    val scheme = MaterialTheme.colorScheme

    val pulse by animateFloatAsState(
        targetValue = if (isRejected) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = MotionTokens.RejectSpringDamping,
            stiffness = MotionTokens.RejectSpringStiffness,
        ),
        label = "zipRejectionPulse",
    )
    // Same pop as the line head (MotionTokens.Trace), so a dot the trace reaches reads as
    // one beat with it; never on first composition, so restoring a board stays still.
    val pop = remember { Animatable(1f) }
    var wasInPath by remember { mutableStateOf(inPath) }
    LaunchedEffect(inPath) {
        val entered = inPath && !wasInPath
        wasInPath = inPath
        if (animated && entered && cell.isWaypoint) {
            val rise = (MotionTokens.Trace.PopDuration * MotionTokens.Trace.PopRiseFraction).toInt()
            pop.animateTo(MotionTokens.Trace.PopScale, tween(rise, easing = MotionTokens.Trace.EasingOut))
            pop.animateTo(1f, tween(MotionTokens.Trace.PopDuration - rise, easing = MotionTokens.EasingStandard))
        } else {
            pop.snapTo(1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(if (isHint) "play.hint_cell" else "play.cell_$idx")
            .graphicsLayer {
                scaleX = pulse * pop.value
                scaleY = pulse * pop.value
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isRejected) {
            Box(Modifier.fillMaxSize().background(scheme.errorContainer.copy(alpha = 0.7f), cellCorner))
        }
        // Ticket 28 r5, hollow EVERYWHERE: one ring renderer for all three states —
        // unvisited and start in the waypointDot role, a traced dot in the powder on-path
        // role (D2, round 3). The label always rides the ring's state color on the
        // surface hole (green 9.70:1 / powder 9.89:1 on boardSurface, both ≥4.5); filled
        // disks are gone. The next-target ring HOLDS the trace-role color while it is the
        // target (persistent step-point cue, user round 6) and plays ONE flash from the
        // state color on transition (MotionTokens.FlashDuration, single event, never a loop); reduced motion
        // settles instantly. Previews render the static state.
        val ringColor = if (inPath) board.waypointDotOnPath else board.waypointDot
        val flash = remember { Animatable(1f) }
        LaunchedEffect(animated, cell.isNextTarget) {
            if (animated && cell.isNextTarget) {
                flash.snapTo(0f)
                flash.animateTo(1f, tween(MotionTokens.FlashDuration))
            } else {
                flash.snapTo(1f)
            }
        }
        val ringPaint = if (cell.isNextTarget) {
            lerp(ringColor, board.waypointDotOnPath, flash.value)
        } else {
            ringColor
        }
        if (cell.isWaypoint) {
            Box(
                Modifier
                    .fillMaxSize(dotFraction)
                    .background(ringPaint, CircleShape)
                    .testTag("play.waypoint_dot_$idx"),
                contentAlignment = Alignment.Center,
            ) {
                // The surface hole: punch level — hides the pipe inside the dot and
                // carries the label's ≥4.5 contrast.
                Box(
                    Modifier
                        .fillMaxSize(
                            (dotFraction - 2 * ZipBoardSpec.startRingFraction) /
                                dotFraction,
                        )
                        .background(board.boardSurface, CircleShape),
                )
            }
        }
        cell.label?.let { label ->
            // Label sized from the dot, not from sp (U1: cap ≈ 0.38 dot Ø), so it scales
            // with the pitch down to n8. Two-digit labels ("10"–"16") fit per U1's glyph
            // widths; any residual overflow is reported, never silently shrunk.
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val pitch = minOf(maxWidth, maxHeight)
                val fontSize = with(LocalDensity.current) {
                    (
                        pitch *
                            dotFraction *
                            ZipBoardSpec.waypointLabelCapFraction *
                            ZipBoardSpec.labelFontPerCap
                        ).toSp()
                }
                Text(
                    text = label.toString(),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = fontSize),
                    color = ringPaint, // label rides the ring (state color, or powder while next target)
                    maxLines = 1,
                )
            }
        }
        // Hint highlight rides the next-target fade + in-path arrow only — no cell
        // border (it read as a selection box, not a hint).
    }
}

/**
 * The trace, hoisted to ONE board-level layer (ticket 28, the user's layer-level
 * subtraction): one fat rounded stroke along the path, drawn UNDER all cell content as
 * the [BoardGrid] underlay, so each dot's surface hole punches the pipe globally. A head
 * disc sits on the line end. In the live game [finger] (board px, read in the draw phase)
 * drives the tip toward cells [hooks] accepts, and [animate] turns on grow-in, retract,
 * tip-home and the head pop ([MotionTokens.Trace]); reduced motion keeps only the tip.
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
            drawTrace(path, finger(), hooks, model.n, board.pathTrace, ZipBoardSpec.pathTraceWidthFraction, animate)
        }
    }
}

/**
 * Ticket 28 r5: the hint arrow — a plain arrowhead glyph CENTERED on the auto-placed
 * segment's head cell, rotated to the segment's direction, sized from the cell in the
 * number labels' size language, in the trace role's color. Nothing else is drawn — the
 * cap-wing chevron (stray pieces past the pipe) is deleted. Labeled heads are skipped:
 * their dot ring plays the one-shot flash instead. The screen hangs it ABOVE the board
 * (sized to it); [alpha] carries the ~2s fade (reduced motion removes the arrow
 * instantly).
 */
@Composable
fun HintArrowHead(arrow: HintArrow, model: ZipRendererModel, alpha: Float, modifier: Modifier = Modifier) {
    val n = model.n
    // A labeled head already announces itself — its dot ring plays the one-shot
    // next-target flash, and a powder glyph over the powder ring/number reads as mush
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

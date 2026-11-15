package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.tracePop
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel

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
    val cell = model.cells[idx]
    // Only this cell's own facts cross into ZipCell, so a board change recomposes just
    // the cells whose face differs (the model itself is unstable and never skips).
    val face = ZipCellFace(
        n = model.n,
        idx = idx,
        label = cell.label,
        isWaypoint = cell.isWaypoint,
        isOnPath = cell.isOnPath,
        isNextTarget = cell.isNextTarget,
        isHint = model.hintHighlight == idx,
        isRejected = model.rejection?.cellIdx == idx,
    )
    ZipCell(face, modifier, animated)
}

/** What one cell draws; equal faces let [ZipCell] skip recomposition. */
@Immutable
internal data class ZipCellFace(
    val n: Int,
    val idx: Int,
    val label: Int?,
    val isWaypoint: Boolean,
    val isOnPath: Boolean,
    val isNextTarget: Boolean,
    val isHint: Boolean,
    val isRejected: Boolean,
)

/** Test seam: called with the cell index each time [ZipCell] actually recomposes. */
internal var zipCellRecomposeProbe: ((Int) -> Unit)? = null

@Composable
internal fun ZipCell(face: ZipCellFace, modifier: Modifier, animated: Boolean) {
    zipCellRecomposeProbe?.invoke(face.idx)
    val idx = face.idx
    val dotFraction = ZipBoardSpec.dotSizeFraction(face.n)
    val inPath = face.isOnPath
    val isHint = face.isHint
    val isRejected = face.isRejected
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
    // A dot the trace reaches pops (MotionTokens.Trace); never on first composition, so
    // restoring a board stays still.
    val pop = remember { Animatable(1f) }
    var wasInPath by remember { mutableStateOf(inPath) }
    LaunchedEffect(inPath) {
        val entered = inPath && !wasInPath
        wasInPath = inPath
        if (animated && entered && face.isWaypoint) {
            pop.tracePop()
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
        // unvisited and start in the waypointDot role, a traced dot in the blue on-path
        // role (D2, round 3). The label always rides the ring's state color on the
        // surface hole (both roles ≥4.5 on boardSurface in each theme); filled
        // disks are gone. The next-target ring HOLDS the trace-role color while it is the
        // target (persistent step-point cue, user round 6) and plays ONE flash from the
        // state color on transition (MotionTokens.FlashDuration, single event, never a loop); reduced motion
        // settles instantly. Previews render the static state.
        val ringColor = if (inPath) board.waypointDotOnPath else board.waypointDot
        val flash = remember { Animatable(1f) }
        LaunchedEffect(animated, face.isNextTarget) {
            if (animated && face.isNextTarget) {
                flash.snapTo(0f)
                flash.animateTo(1f, tween(MotionTokens.FlashDuration))
            } else {
                flash.snapTo(1f)
            }
        }
        val ringPaint = if (face.isNextTarget) {
            lerp(ringColor, board.waypointDotOnPath, flash.value)
        } else {
            ringColor
        }
        if (face.isWaypoint) {
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
        face.label?.let { label ->
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
                    color = ringPaint, // label rides the ring (state color, or the trace blue while next target)
                    maxLines = 1,
                )
            }
        }
        // Hint highlight rides the next-target fade + in-path arrow only — no cell
        // border (it read as a selection box, not a hint).
    }
}

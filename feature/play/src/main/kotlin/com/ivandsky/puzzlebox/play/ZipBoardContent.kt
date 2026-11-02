package com.ivandsky.puzzlebox.play

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.BoardTokens
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.EdgeOverlay
import com.ivandsky.puzzlebox.gameui.EdgeSide
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.rememberReducedMotion
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The F2-1 Compose mapping, owned HERE (the state→model call site is the play feature;
 * game-ui never imports Zip): [ZipRendererModel] → the shared [BoardGrid]. Registered
 * into the [GameUiRegistry] by `playModule`; the interactive play screen reuses the same
 * outline + wall overlays + cell decor inside a [com.ivandsky.puzzlebox.gameui.DragTraceBoard].
 */
fun GameUiRegistry.registerZipBoard() {
    register(ZipDefinition.id) { model, modifier ->
        ZipBoard(model as ZipRendererModel, modifier)
    }
}

/** Read-only board (no gesture layer): the registry-facing rendering of the model. */
@Composable
fun ZipBoard(model: ZipRendererModel, modifier: Modifier = Modifier) {
    BoardGrid(
        rows = model.n,
        cols = model.n,
        modifier = modifier.zipBoardOutline(model.n),
        edgeOverlays = zipWallOverlays(model),
        underlay = { ZipPipes(model) },
        cellContent = { row, col -> ZipCellDecor(model, row, col) },
    )
}

fun zipWallOverlays(model: ZipRendererModel): List<EdgeOverlay.Wall> =
    model.walls.map { wall ->
        EdgeOverlay.Wall(
            cell = GridCell(wall.cellIdx / model.n, wall.cellIdx % model.n),
            side = EdgeSide.valueOf(wall.direction.name),
        )
    }

/**
 * Ticket 22 board proportions, all as fractions of the cell pitch so the decor scales
 * n5→n8 and phone→tablet. Values are research U1's LinkedIn measurements
 * (`.scratch/puzzlebox/research/uiux-01-linkedin-visual-spec.md`); the T25 matrix gate
 * retunes through these names. They live here — not in [BoardTokens] — because token-file
 * edits are out of ticket 22's scope; promoting them into the token file is a gate decision.
 */
internal object ZipBoardSpec {
    /** A6 floor / U1 mobile: waypoint dot Ø as a fraction of the cell pitch (n ≤ 7). */
    const val waypointDotSizeFraction = 0.60f

    /**
     * D5 gate resolution (2026-11-01): at n8 the two-digit labels measured 9.35dp cap —
     * under the 10.3dp senior-UX comfort floor. 0.66 at n ≥ 8 lands the cap exactly on
     * the floor; neighbor gap stays ~14dp (no merge). Softer trace blue (ticket 27)
     * keeps the bigger dots from glaring. Apply via [dotSizeFraction].
     */
    const val n8WaypointDotSizeFraction = 0.66f

    /** Dot fraction for a board size: n8+ carries the D5 label-legibility bump. */
    fun dotSizeFraction(n: Int): Float =
        if (n >= 8) n8WaypointDotSizeFraction else waypointDotSizeFraction

    /** U1 mobile: label cap height as a fraction of the dot Ø, sized from the dot (not sp). */
    const val waypointLabelCapFraction = 0.38f

    /** Cap-height → font-size conversion for the board's rounded display face (≈ 1 / 0.71). */
    const val labelFontPerCap = 1.40f

    /** U1 web desktop: trace stroke as a fraction of the pitch (mobile promo measured 0.89). */
    const val pathTraceWidthFraction = 0.54f

    /** U1 mobile halo ≈ 3.5px on a 62px pitch: separation ring under dots sitting on the trace. */
    const val waypointHaloFraction = 0.055f

    /** Start waypoint's hollow-variant ring thickness, as a fraction of the pitch. */
    const val startRingFraction = 0.05f

    /** Ticket 28: next-target highlight — full color-fade loop duration (ms). */
    const val nextTargetFadeMs = 1600

    /** Ticket 28: hint chevron — stroke width and wing length, as fractions of the pitch. */
    const val hintArrowWidthFraction = 0.085f
    const val hintArrowLengthFraction = 0.15f

    /** U1 §4: board outline corner radius, measured 0.35–0.4 cell (midpoint picked). */
    const val boardOutlineRadiusFraction = 0.375f
}

/**
 * U1 §4: Zip has no wall element — the board outline IS the gridline stroke, drawn as a
 * rounded rectangle (radius ≈ 0.375 cell), inset by half the stroke so it stays inside
 * the board bounds. Board-level decoration, so both the read-only [ZipBoard] and the play
 * screen's DragTraceBoard hang it on their board modifiers.
 */
@Composable
internal fun Modifier.zipBoardOutline(n: Int): Modifier {
    val board = LocalBoardColors.current
    val weight = BoardTokens.boardGridLineWeight
    return drawBehind {
        val stroke = weight.toPx()
        val half = stroke / 2f
        drawRoundRect(
            color = board.boardGridLine,
            topLeft = Offset(half, half),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(size.minDimension / n * ZipBoardSpec.boardOutlineRadiusFraction),
            style = Stroke(width = stroke),
        )
    }
}

private val cellCorner = RoundedCornerShape(10.dp)

/**
 * One cell: LinkedIn-style waypoint dots with the bold label INSIDE the dot, the hint
 * ring, and the transient rejection pulse. The trace is NOT drawn here any more — it
 * lives in the board-level [ZipPipes] layer (ticket 28) under every cell's content, so
 * each dot's surface hole punches the pipe globally instead of per-cell caps clipping
 * into neighbor dots. Sizes come from [ZipBoardSpec] fractions of the cell. Resting by
 * contract (A8): the next-target breath animates only in the live game — the play screen
 * passes [animatePulse]; previews and read-only boards keep the static ring.
 */
@Composable
fun ZipCellDecor(
    model: ZipRendererModel,
    row: Int,
    col: Int,
    modifier: Modifier = Modifier,
    animatePulse: Boolean = false,
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
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "zipRejectionPulse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(if (isHint) "play.hint_cell" else "play.cell_$idx")
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isRejected) {
            Box(Modifier.fillMaxSize().background(scheme.errorContainer.copy(alpha = 0.7f), cellCorner))
        }
        // Ticket 28, hollow EVERYWHERE: one ring renderer for all three states — unvisited
        // and start in the waypointDot role, a traced dot in the powder on-path role (D2,
        // round 3). The label always rides the ring's own color on the surface hole (green
        // 9.70:1 / powder 9.89:1 on boardSurface, both ≥4.5); filled disks are gone. The
        // next waypoint's ring fades into the tertiary role and back (~1.6s loop) in the
        // live game only — color beats scale for visibility at dot sizes; reduced motion
        // and previews render the static ring.
        var ringColor = if (inPath) board.waypointDotOnPath else board.waypointDot
        if (animatePulse && cell.isNextTarget) {
            val phase by rememberInfiniteTransition(label = "zipNextTargetFade").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = ZipBoardSpec.nextTargetFadeMs
                        0f at 0
                        1f at (ZipBoardSpec.nextTargetFadeMs / 2) using FastOutSlowInEasing
                        0f at ZipBoardSpec.nextTargetFadeMs using FastOutSlowInEasing
                    },
                ),
                label = "zipNextTargetFadePhase",
            )
            ringColor = lerp(ringColor, scheme.tertiary, phase)
        }
        if (cell.isWaypoint) {
            Box(
                Modifier
                    .fillMaxSize(dotFraction)
                    .background(ringColor, CircleShape)
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
                    color = ringColor, // hollow everywhere: the label rides the ring's color
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
 * subtraction): one fat rounded stroke per step, drawn UNDER all cell content as the
 * [BoardGrid] underlay. Each dot's surface hole then punches the pipe globally — no
 * per-cell cap can clip into a neighbor dot hole, and the old per-cell double-draw seams
 * at cell boundaries are gone. ZipBoard and the play screen hang this identically.
 */
@Composable
fun ZipPipes(model: ZipRendererModel) {
    val board = LocalBoardColors.current
    if (model.path.size < 2) return
    Canvas(Modifier.fillMaxSize()) {
        val pitchX = size.width / model.n
        val pitchY = size.height / model.n
        val stroke = minOf(pitchX, pitchY) * ZipBoardSpec.pathTraceWidthFraction
        val centers = model.path.map { idx ->
            Offset((idx % model.n + 0.5f) * pitchX, (idx / model.n + 0.5f) * pitchY)
        }
        for (i in 1 until centers.size) {
            drawLine(
                color = board.pathTrace,
                start = centers[i - 1],
                end = centers[i],
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Ticket 28: the hint arrow — a chevron INSIDE the trace pipe at the auto-placed
 * segment's head pointing along the draw direction, in the trace role's color. Wings sit
 * on the round cap, tip stays inside the pipe (no overhang past the path). Number cells
 * get NO arrow — their waypoint ring already carries the next-target fade. The screen
 * hangs it ABOVE the board (sized to it). [alpha] carries the ~2s fade (reduced motion
 * removes the arrow instantly).
 */
@Composable
fun HintArrowHead(arrow: HintArrow, model: ZipRendererModel, alpha: Float, modifier: Modifier = Modifier) {
    val n = model.n
    // A labeled cell announces itself via its ring's next-target fade — an arrow over the
    // number reads as clutter (user finding, board-review round 2).
    if (model.cells[arrow.headIdx].label != null) return
    val board = LocalBoardColors.current
    Canvas(modifier.graphicsLayer { this.alpha = alpha }.testTag("play.hint_arrow")) {
        val headCol = arrow.headIdx % n
        val headRow = arrow.headIdx / n
        val dx = headCol - arrow.tailIdx % n
        val dy = headRow - arrow.tailIdx / n
        if (dx == 0 && dy == 0) return@Canvas // degenerate first placement: no direction
        val pitch = minOf(size.width / n, size.height / n)
        val dir = Offset(dx.toFloat(), dy.toFloat()) // adjacent cells: already unit length
        val capR = pitch * ZipBoardSpec.pathTraceWidthFraction / 2f
        val headCenter = Offset(
            (headCol + 0.5f) * (size.width / n),
            (headRow + 0.5f) * (size.height / n),
        )
        // Arrowhead INSIDE the pipe: wings on the cap circle, tip well inside the trace
        // stroke — the chevron reads as part of the path, not a marker on top of it.
        val tip = headCenter + dir * (capR * 0.35f)
        val theta = 42f * (PI.toFloat() / 180f)
        fun wing(sign: Float): Offset {
            val cosA = cos(sign * theta)
            val sinA = sin(sign * theta)
            return headCenter + Offset(
                dir.x * cosA - dir.y * sinA,
                dir.x * sinA + dir.y * cosA,
            ) * capR
        }
        val w1 = wing(-1f)
        val w2 = wing(1f)
        val chevron = Path().apply {
            moveTo(w1.x, w1.y)
            lineTo(tip.x, tip.y)
            lineTo(w2.x, w2.y)
        }
        drawPath(
            chevron,
            color = board.pathTrace,
            style = Stroke(
                width = pitch * ZipBoardSpec.hintArrowWidthFraction,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Previews (A7/A8 — resting states only, registered for the T25 variant matrix).
// ---------------------------------------------------------------------------

/** Preview fixture: an n-sized board with the given waypoint cells and trace. */
internal fun zipPreviewModel(n: Int, waypoints: List<Int>, path: List<Int> = emptyList()): ZipRendererModel {
    val board = ZipBoard(n, emptySet(), waypoints.map(::Cell))
    val puzzle = ZipPuzzle(VariantId("zip/n$n"), board, null)
    return ZipRendererModel.of(ZipState(puzzle, path.map(::Cell)), null)
}

@PuzzleBoxPreviews
@Composable
internal fun ZipBoardDotsFreshPreview() {
    PuzzleBoxTheme {
        // n6 fresh: dots at the A6 proportion, hollow start ring, no trace.
        ZipBoard(zipPreviewModel(6, waypoints = listOf(0, 7, 20, 28, 35)), Modifier.padding(24.dp))
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ZipBoardDotsMidTracePreview() {
    PuzzleBoxTheme {
        // n6 mid-trace: fat token trace under the dots, halos on visited waypoints,
        // the traced start reading as a visited dot, waypoint 3 as the next target.
        ZipBoard(
            zipPreviewModel(
                6,
                waypoints = listOf(0, 5, 18, 30, 35),
                path = listOf(0, 6, 7, 8, 14, 15, 16, 17, 11, 5),
            ),
            Modifier.padding(24.dp),
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ZipBoardDotsN8FreshPreview() {
    PuzzleBoxTheme {
        // n8 with all 16 waypoints, two-digit labels "10"–"16" included; the last four
        // dots sit on adjacent cells — the no-merge stress case.
        ZipBoard(zipPreviewModel(8, waypoints = (0 until 16).map { it * 4 }), Modifier.padding(24.dp))
    }
}

/**
 * T25 gate visual (brief 22 §4): the U1-measured red→orange path gradient next to the
 * flat `pathTrace` token, same S-path. Preview-only swatch — production keeps the token;
 * the gradient hexes are U1's measured endpoints and ship only if the gate picks them.
 */
@PuzzleBoxPreviews
@Composable
internal fun ZipTraceGradientVsTokenPreview() {
    PuzzleBoxTheme {
        val board = LocalBoardColors.current
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("pathTrace token", style = MaterialTheme.typography.labelLarge)
            Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                drawSSample(SolidColor(board.pathTrace))
            }
            Spacer(Modifier.height(8.dp))
            Text("U1 gradient #EA3328→#F27B16", style = MaterialTheme.typography.labelLarge)
            Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                drawSSample(
                    Brush.linearGradient(listOf(Color(0xFFEA3328), Color(0xFFF27B16))),
                )
            }
        }
    }
}

private fun DrawScope.drawSSample(brush: Brush) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.1f, h * 0.2f)
        cubicTo(w * 0.4f, h * 0.2f, w * 0.4f, h * 0.8f, w * 0.5f, h * 0.8f)
        cubicTo(w * 0.6f, h * 0.8f, w * 0.6f, h * 0.2f, w * 0.9f, h * 0.2f)
    }
    drawPath(
        path,
        brush,
        style = Stroke(width = h * 0.35f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

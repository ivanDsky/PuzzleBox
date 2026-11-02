package com.ivandsky.puzzlebox.play

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel

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
 * One cell: the fat rounded trace (A2 — under everything), LinkedIn-style waypoint dots
 * with the bold label INSIDE the dot, the hint ring, and the transient rejection pulse.
 * Sizes come from [ZipBoardSpec] fractions of the cell. Static by contract (A8): the
 * breathing next-target is a wave-2 ticket — the model already carries the flag.
 */
@Composable
fun ZipCellDecor(model: ZipRendererModel, row: Int, col: Int, modifier: Modifier = Modifier) {
    val idx = row * model.n + col
    val dotFraction = ZipBoardSpec.dotSizeFraction(model.n)
    val cell = model.cells[idx]
    val pathIndex = model.path.indexOf(idx)
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
        if (inPath) {
            Connectors(model, pathIndex)
        }
        if (cell.isWaypoint) {
            if (inPath) {
                // D2 gate resolution (round 3): hollow on-path dot — trace-role ring on a
                // boardSurface hole, label in the trace role's color (dark #B3CBD6 9.89:1 /
                // light #0F78B4 4.81:1 on surface, both ≥4.5). Replaces the filled dot +
                // boardSurface halo, whose dark ring read as cuts in the pipe; the hollow
                // ring needs no halo — the surface hole separates it from the trace by
                // luminance in both themes. Ring thickness = start ring (visual set).
                Box(
                    Modifier
                        .fillMaxSize(dotFraction)
                        .background(board.waypointDotOnPath, CircleShape)
                        .testTag("play.waypoint_dot_$idx"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize(
                                (dotFraction - 2 * ZipBoardSpec.startRingFraction) /
                                    dotFraction,
                            )
                            .background(board.boardSurface, CircleShape),
                    )
                }
            } else if (cell.isStart) {
                // Start waypoint (U1 evidence is thin — treatment flagged for the T25
                // gate): hollow variant per U2's alternative pairing — waypointDot ring on
                // a boardSurface fill, label in the dot role's own color. Contrast is the
                // dot-vs-surface pair (dark 9.70:1 / light 6.03:1) in both themes. Once
                // traced, the start reads as any visited dot (on-path role).
                Box(
                    Modifier
                        .fillMaxSize(dotFraction)
                        .background(board.waypointDot, CircleShape)
                        .testTag("play.waypoint_dot_$idx"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize(
                                (dotFraction - 2 * ZipBoardSpec.startRingFraction) /
                                    dotFraction,
                            )
                            .background(board.boardSurface, CircleShape),
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize(dotFraction)
                        .background(board.waypointDot, CircleShape)
                        .testTag("play.waypoint_dot_$idx"),
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
                    color = when {
                        inPath -> board.waypointDotOnPath // hollow: label on surface in the ring's color
                        cell.isStart -> board.waypointDot // hollow start: label in the ring's color
                        else -> board.waypointLabel
                    },
                    maxLines = 1,
                )
            }
        }
        if (isHint) {
            Box(Modifier.fillMaxSize(0.9f).border(2.dp, scheme.tertiary, cellCorner))
        }
    }
}

/** The trace: one fat rounded stroke per step (U1 — cap radius = half stroke, so elbows and the moving head round themselves). Drawn under the dots (A2). */
@Composable
private fun BoxScope.Connectors(model: ZipRendererModel, pathIndex: Int) {
    val board = LocalBoardColors.current
    val after = model.path.getOrNull(pathIndex + 1)
    val before = model.path.getOrNull(pathIndex - 1)
    val here = model.path[pathIndex]
    Canvas(Modifier.matchParentSize()) {
        val stroke = size.minDimension * ZipBoardSpec.pathTraceWidthFraction
        val mid = Offset(size.width / 2f, size.height / 2f)
        for (neighbor in listOfNotNull(before, after)) {
            val end = when (neighbor - here) {
                1 -> Offset(size.width, mid.y) // right neighbor
                -1 -> Offset(0f, mid.y) // left neighbor
                model.n -> Offset(mid.x, size.height) // cell below
                else -> Offset(mid.x, 0f) // cell above
            }
            drawLine(
                color = board.pathTrace,
                start = mid,
                end = end,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
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

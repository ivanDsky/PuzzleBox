package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.designsystem.BoardTokens
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.EdgeOverlay
import com.ivandsky.puzzlebox.gameui.EdgeSide
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel

/**
 * Read-only board (no gesture layer) — the F2-1 Compose mapping, owned by Zip's UI
 * module (game-ui never imports Zip): [ZipRendererModel] → the shared [BoardGrid]. The
 * interactive play screen reuses the same outline + wall overlays + cell decor inside a
 * [com.ivandsky.puzzlebox.gameui.DragTraceBoard].
 */
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

    /** Start waypoint's hollow-variant ring thickness, as a fraction of the pitch. */
    const val startRingFraction = 0.05f

    /** Round-5 arrow (user: → not >, black/subtracted, never wider than the pipe):
     *  shaft + head glyph drawn in boardSurface over the blue pipe — a knockout in the
     *  trace, same visual language as the dot holes. Anchors along the direction
     *  (fractions of pitch, signed from the head-cell center): the visible pipe ends at
     *  the segment cap +0.27, so the glyph stays within ±0.24 — nothing hangs past the
     *  line. Cross extents ≤ head half 0.20 ≤ pipe half-width 0.27. */
    const val hintArrowShaftBackFraction = 0.19f
    const val hintArrowShaftFrontFraction = 0.03f
    const val hintArrowTipFraction = 0.17f
    const val hintArrowShaftHalfThicknessFraction = 0.055f
    const val hintArrowHeadHalfWidthFraction = 0.16f

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

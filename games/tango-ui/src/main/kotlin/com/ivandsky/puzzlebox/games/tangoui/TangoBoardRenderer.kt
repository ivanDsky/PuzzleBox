package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.ivandsky.puzzlebox.designsystem.BoardTokens
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightOverlay
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.EdgeOverlay
import com.ivandsky.puzzlebox.gameui.EdgeSide
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel


/**
 * Maps [TangoRendererModel] onto the shared game-ui [BoardGrid] (design-direction
 * §2–§7): sun/moon glyphs as vectors in cell content (silhouette identity, §3 fills),
 * `=`/`×` constraints as non-interactive [EdgeOverlay.Mark] decorations (A12),
 * violations as tint + dashed border (the rule-naming caption is the shared
 * chrome's, via [TangoPlayController.violation]), a guided lesson's answer as faint
 * ghost glyphs ([TangoRendererModel.ghosts]), per-cell TalkBack semantics (§9),
 * tap-cycle placement via [TangoTapPolicy].
 */
class TangoBoardRenderer(
    private val onPlacement: ((TangoPlacement) -> Unit)? = null,
    private val spotlight: () -> Spotlight? = { null },
) {

    @Composable
    fun Content(model: RendererModel, modifier: Modifier) {
        val tango = model as? TangoRendererModel
            ?: error("TangoBoardRenderer expects TangoRendererModel, got ${model::class.simpleName}")
        val commit = onPlacement

        Column(modifier) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val pitch = maxWidth / tango.n
                BoardGrid(
                    rows = tango.n,
                    cols = tango.n,
                    modifier = Modifier
                        .aspectRatio(1f)
                        // Close the mesh: without an outer border the top row reads
                        // as glyphs floating above the board (user QA 2026-11-02).
                        .border(
                            width = BoardTokens.boardGridLineWeight * 2,
                            color = LocalBoardColors.current.boardGridLine,
                        ),
                    markStyleOverride = MaterialTheme.typography.labelMedium.copy(
                        // Mark font, fraction of pitch; the drawn `=`/`×` ink per
                        // size is in markTextFraction's KDoc (TangoMarkInkTest).
                        fontSize = with(LocalDensity.current) {
                            (pitch * TangoBoardSpec.markTextFraction(tango.n)).toSp()
                        },
                    ),
                    markColorOverride = MaterialTheme.colorScheme.onSurface,
                    // LinkedIn chip treatment: opaque disc under the symbol, drawn
                    // above the cell layer so the mark never hides under glyphs.
                    markBadge = true,
                    overlaysAboveCells = true,
                    edgeOverlays = tango.markers.map { marker ->
                        EdgeOverlay.Mark(
                            cell = GridCell(
                                row = marker.cellIdx / tango.n,
                                col = marker.cellIdx % tango.n,
                            ),
                            side = when (marker.side) {
                                TangoEdgeSide.RIGHT -> EdgeSide.RIGHT
                                TangoEdgeSide.DOWN -> EdgeSide.DOWN
                            },
                            symbol = when (marker.kind) {
                                TangoMarkerKind.SAME -> "="
                                TangoMarkerKind.OPPOSITE -> "×"
                            },
                        )
                    },
                    onCellTap = commit?.let { onCommit ->
                        { row: Int, col: Int ->
                            TangoTapPolicy.onTap(tango, row * tango.n + col).forEach(onCommit)
                        }
                    },
                    cellContent = { row, col ->
                        Cell(
                            model = tango,
                            onPlacement = commit,
                            idx = row * tango.n + col,
                            row = row,
                            col = col,
                        )
                    },
                )
                // Above cells, marks, tint and frame; no pointer input, so taps reach the board. The square
                // keeps it on the board when the pane is wider than the board is tall (00 §3).
                SpotlightOverlay(
                    spotlight(),
                    Modifier.wrapContentSize(Alignment.TopStart).aspectRatio(1f).testTag("tango.spotlight"),
                )
            }
        }
    }
}

@Composable
private fun Cell(
    model: TangoRendererModel,
    onPlacement: ((TangoPlacement) -> Unit)?,
    idx: Int,
    row: Int,
    col: Int,
) {
    val render = model.cells.getOrNull(idx)
    val violationPhrase = TangoSemantics.violationPhraseAt(model, idx)
    val violating = violationPhrase != null
    val hinted = model.hintCell == idx
    val scheme = MaterialTheme.colorScheme
    val board = LocalBoardColors.current
    val gridLineWeight = BoardTokens.boardGridLineWeight
    val reducedMotion = rememberReducedMotion()
    // ViolationFlash retired (user QA 2026-11-02): the 1→0.86 cell scale read as a
    // permanently shrunken glyph. Tint + dashed border carry the state (§6).

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Reference pitch is irrelevant to shape: fractions read the cell box,
                // dashes read the pitch-equivalent cell size (minDimension = pitch px).
                val pitchPx = size.minDimension
                // Square corners (user QA 2026-11-02): the board is a squared
                // gridline mesh — state washes follow the cells, not tiles.
                val stateRadius = CornerRadius.Zero
                // Given identity (user QA 2026-11-02): preplaced cells read as DISABLED —
                // a muted background wash, not a dot. Drawn before the violation tint so
                // a given caught in a violating line still shows the error state above it.
                if (render?.isGiven == true && render.value != null) {
                    drawRoundRect(
                        color = scheme.surfaceVariant.copy(alpha = TangoBoardSpec.givenTintAlpha),
                        cornerRadius = stateRadius,
                    )
                }
                if (hinted) {
                    // HintReveal (§2/§7, spec A14): an inset orange tile on its own lip —
                    // state-driven highlight, persists until the next input clears it.
                    val inset = TangoBoardSpec.hintTileInset.toPx()
                    val depth = TangoBoardSpec.hintTileLipDepth.toPx()
                    val corner = CornerRadius(pitchPx * TangoBoardSpec.hintTileCornerFraction)
                    val face = Size(size.width - inset * 2, size.height - inset * 2 - depth)
                    drawRoundRect(board.tangoHintLip, Offset(inset, inset + depth), face, corner)
                    drawRoundRect(board.tangoHintFill, Offset(inset, inset), face, corner)
                }
                if (violating) {
                    // errorContainer tint 0.7 (Zip rejection precedent) + dashed border
                    // in colorScheme.error — the shape signal keeps color from being
                    // the sole carrier (§2; tint alone is 1.29:1 on white, by design).
                    drawRoundRect(
                        color = scheme.errorContainer.copy(alpha = TangoBoardSpec.violationTintAlpha),
                        cornerRadius = stateRadius,
                    )
                    drawRoundRect(
                        color = scheme.error,
                        cornerRadius = stateRadius,
                        style = Stroke(
                            width = gridLineWeight.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(
                                    pitchPx * TangoBoardSpec.violationDashFraction,
                                    pitchPx * TangoBoardSpec.violationDashGapFraction,
                                ),
                            ),
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        render?.value?.let { Glyph(it, Modifier.fillMaxSize(), model.n) }
        // Guide ghost (spec A12): the lesson's answer, faint, until the square matches —
        // full size on an empty square, a corner badge beside a wrong glyph.
        model.ghosts[idx]?.let { answer ->
            val badge = Modifier.align(Alignment.TopEnd).fillMaxSize(TangoBoardSpec.ghostBadgeFraction)
            Glyph(answer, if (render?.value == null) Modifier.fillMaxSize() else badge, model.n, TangoBoardSpec.ghostAlpha)
        }

        // Per-cell TalkBack semantics (§9): one focusable node per cell, row-major;
        // givens distinguished by words ("given sun"), not visuals (§2), and neither
        // givens nor an inert surface (no placement callback) expose custom actions —
        // an action that does nothing lies about the surface (§2).
        val actionLabels =
            if (render?.isGiven == true || onPlacement == null) emptyList() else TangoSemantics.customActions
        Box(
            Modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        listOf(TangoSemantics.cellContentDescription(row, col))
                            .plus(TangoSemantics.markerPhrases(model, idx))
                            .joinToString(separator = "; ")
                    stateDescription = TangoSemantics.cellStateDescription(model, idx)
                    customActions = actionLabels.map { label ->
                        // Non-empty only with a live placement callback (above).
                        CustomAccessibilityAction(label) {
                            TangoTapPolicy.onCustomAction(model, idx, label).forEach(onPlacement!!)
                            true
                        }
                    }
                },
        )
    }
}

/** The sun or moon in its board fill, at [alpha] of that fill's own alpha (1 = the placed glyph). */
@Composable
private fun Glyph(value: TangoCell, modifier: Modifier, n: Int, alpha: Float = 1f) {
    val colors = LocalBoardColors.current
    when (value) {
        TangoCell.SUN -> SunGlyph(modifier, colors.sunFill.copy(alpha = colors.sunFill.alpha * alpha), n)
        TangoCell.MOON -> MoonGlyph(modifier, colors.moonFill.copy(alpha = colors.moonFill.alpha * alpha), n)
    }
}

@Composable
private fun SunGlyph(modifier: Modifier, fill: Color, n: Int) = Canvas(modifier) { drawSun(fill, n) }

@Composable
private fun MoonGlyph(modifier: Modifier, fill: Color, n: Int) {
    val paths = remember { MoonPaths() }
    Canvas(modifier) { drawMoon(fill, n, paths) }
}

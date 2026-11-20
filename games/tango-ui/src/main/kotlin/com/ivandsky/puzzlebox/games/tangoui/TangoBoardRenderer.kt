package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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
import com.ivandsky.puzzlebox.gameui.replay.ReplayHintBadge
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel

/** The overlay's box is the board's: square and top-start, even in a pane wider than the board is tall (00 §3). */
private val SpotlightOnBoard = Modifier.wrapContentSize(Alignment.TopStart).aspectRatio(1f).testTag("tango.spotlight")

/**
 * Maps [TangoRendererModel] onto the shared game-ui [BoardGrid] (design-direction
 * §2–§7): sun/moon glyphs as vectors in cell content (silhouette identity, §3 fills),
 * `=`/`×` constraints as non-interactive [EdgeOverlay.Mark] decorations (A12),
 * violations as tint + dashed border (the rule-naming caption is the shared
 * chrome's, via [TangoPlayController.violation]), a guided lesson's answer as faint
 * ghost glyphs ([TangoRendererModel.ghosts]), per-cell TalkBack semantics (§9),
 * tap-cycle placement via [onTap] (the owner reads the live session — [TangoPlayController.onTap]).
 *
 * [onTap] gets the tapped cell index, never a model-derived placement: a step computed from
 * the model this board last composed with is stale until the next frame, so a fast second
 * tap repeated the first and no-opped (M4-tango-28). [onPlacement] lands TalkBack's custom
 * actions; null for both = an inert board (the editor's preview). [hinted] cells wear the replay
 * hint badge in their top-right corner (a replay board only; live play passes none). [glyphFade],
 * set by the replay board only, fades a glyph in, out and between sun and moon on that spec
 * (`snap()` = at once); null, as live play passes, draws the glyph as is. [guideGhosts] (live play) draws a guided
 * lesson's ghosts as invitations: stronger glyph and a dashed outline on an open square (#1186); the editor keeps its faint hints.
 * [overlay] is drawn after the [spotlight], inside the grid's bounds: the Trainer's practice layer (it may take touches).
 */
class TangoBoardRenderer(
    private val onTap: ((idx: Int) -> Unit)? = null,
    private val onPlacement: ((TangoPlacement) -> Unit)? = null,
    private val spotlight: () -> Spotlight? = { null },
    private val guideGhosts: Boolean = false,
    private val overlay: (@Composable BoxScope.() -> Unit)? = null,
) {

    @Composable
    fun Content(
        model: RendererModel,
        modifier: Modifier,
        hinted: Set<Int> = emptySet(),
        glyphFade: FiniteAnimationSpec<Float>? = null,
    ) {
        val tango = model as? TangoRendererModel
            ?: error("TangoBoardRenderer expects TangoRendererModel, got ${model::class.simpleName}")
        val commit = onPlacement
        val n = tango.n
        // Stable across placements: BoardGrid keys its tap detector on this lambda, and a
        // new one per model restarted the detector, dropping a tap in flight (M4-tango-28).
        val onCellTap = remember(this, n) { onTap?.let { tap -> { row: Int, col: Int -> tap(row * n + col) } } }
        val style = rememberTangoBoardStyle()

        Column(modifier) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val pitch = maxWidth / tango.n
                BoardGrid(
                    rows = tango.n,
                    cols = tango.n,
                    // The frame and the sign ink follow the board tokens (TangoReadableBoardStyle).
                    modifier = Modifier.aspectRatio(1f).then(style.frame),
                    markStyleOverride = MaterialTheme.typography.labelMedium.copy(
                        // Mark font, fraction of pitch; the drawn `=`/`×` ink per
                        // size is in markTextFraction's KDoc (TangoMarkInkTest).
                        fontSize = with(LocalDensity.current) {
                            (pitch * TangoBoardSpec.markTextFraction(tango.n)).toSp()
                        },
                    ),
                    markColorOverride = style.signInk,
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
                    onCellTap = onCellTap,
                    cellContent = { row, col ->
                        Cell(
                            model = tango,
                            style = style,
                            guideGhosts = guideGhosts,
                            onPlacement = commit,
                            hinted = (row * tango.n + col) in hinted,
                            glyphFade = glyphFade,
                            idx = row * tango.n + col,
                            row = row,
                            col = col,
                        )
                    },
                )
                // Above cells, marks, tint and frame; no pointer input, so taps reach the board.
                SpotlightOverlay(spotlight(), SpotlightOnBoard)
                overlay?.let { Box(Modifier.wrapContentSize(Alignment.TopStart).aspectRatio(1f)) { it() } }
            }
        }
    }
}

@Composable
private fun Cell(
    model: TangoRendererModel,
    style: TangoReadableBoardStyle,
    guideGhosts: Boolean,
    onPlacement: ((TangoPlacement) -> Unit)?,
    hinted: Boolean,
    glyphFade: FiniteAnimationSpec<Float>?,
    idx: Int,
    row: Int,
    col: Int,
) {
    val render = model.cells.getOrNull(idx)
    val violationPhrase = TangoSemantics.violationPhraseAt(model, idx)
    val violating = violationPhrase != null
    val scheme = MaterialTheme.colorScheme
    val inviting = guideGhosts && render?.value == null && model.ghosts.containsKey(idx)
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
                // A given has no wash; it is told by its sun or moon colour alone.
                // An open guide square: a dashed accent outline reads as "tap here" (#1186).
                if (inviting) drawGhostOutline(scheme.primary, gridLineWeight.toPx())
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
        if (glyphFade == null) {
            render?.value?.let { Glyph(it, Modifier.fillMaxSize(), model.n) }
        } else {
            Crossfade(render?.value, Modifier.fillMaxSize(), glyphFade, label = "tango.glyph") { value ->
                value?.let { Glyph(it, Modifier.fillMaxSize(), model.n) }
            }
        }
        // Guide ghost (spec A12): the lesson's answer, faint, until the square matches —
        // full size on an empty square, a corner badge beside a wrong glyph.
        model.ghosts[idx]?.let { answer ->
            val badge = Modifier.align(Alignment.TopEnd).fillMaxSize(TangoBoardSpec.ghostBadgeFraction)
            Glyph(answer, if (render?.value == null) Modifier.fillMaxSize() else badge, model.n, if (guideGhosts) TangoBoardSpec.guideGhostAlpha else TangoBoardSpec.ghostAlpha)
        }
        // Inset inside the corner: on the top row a flush badge was cut by the board border.
        if (hinted) BoxWithConstraints(Modifier.fillMaxSize()) {
            ReplayHintBadge(Modifier.align(Alignment.TopEnd).padding(maxWidth * TangoBoardSpec.replayHintInsetFraction))
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

/**
 * The sun or moon in its board fill, at [alpha] of that fill's own alpha (1 = the placed glyph).
 */
@Composable
private fun Glyph(value: TangoCell, modifier: Modifier, n: Int, alpha: Float = 1f) {
    val colors = LocalBoardColors.current
    when (value) {
        TangoCell.SUN -> SunGlyph(modifier, colors.sunFill.copy(alpha = colors.sunFill.alpha * alpha), n)
        TangoCell.MOON -> MoonGlyph(modifier, colors.moonFill.copy(alpha = colors.moonFill.alpha * alpha), n)
    }
}

@Composable
private fun SunGlyph(modifier: Modifier, fill: Color, n: Int) = Canvas(modifier) {
    drawSun(fill, n)
}

@Composable
private fun MoonGlyph(modifier: Modifier, fill: Color, n: Int) {
    val paths = remember { MoonPaths() }
    Canvas(modifier) {
        drawMoon(fill, n, paths)
    }
}

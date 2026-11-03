package com.ivandsky.puzzlebox.games.queensui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.GridGeometry

/** True when system animations are off — every board entrance/exit collapses to an
 *  instant state change (direction doc §5 reduced-motion rule). Read once at board
 *  level and provided down. */
val LocalQueensReducedMotion = staticCompositionLocalOf { false }

@Composable
internal fun rememberReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * The full Queens board UI (queens-08): provenance-distinct X marks (§3.2/§3.3),
 * pop-in queens (§3.4), the ALWAYS-ON A14 violation stripes (fix round 6), the §5
 * interaction set on [QueensBoardState], and the §5.5 staged hint (region pulse +
 * deduction, crown ghost on the repeat). The win presentation is NOT here: the solved
 * session hands off to the opaque result ROUTE at screen level (fix round 6 — Zip's
 * ResultScreen presentation; the modal overlay is deleted), so the board never renders
 * win content. Gesture surface is board-level with edge-clamped cell resolution (§6);
 * all game decisions stay in the session/state holder.
 */
@Composable
fun QueensPlayBoard(
    state: QueensBoardState,
    modifier: Modifier = Modifier,
    variants: QueensAbVariants = QueensAbVariants(),
) {
    val reducedMotion = rememberReducedMotion()
    CompositionLocalProvider(LocalQueensReducedMotion provides reducedMotion) {
        QueensPlayBoardContent(state, modifier, variants)
    }
}

@Composable
private fun QueensPlayBoardContent(
    state: QueensBoardState,
    modifier: Modifier,
    variants: QueensAbVariants,
) {
    val model = QueensBoardModel.of(state.session, state.hint, state.hintPointed)
    val boardTokens = LocalBoardColors.current
    val haptics = LocalHapticFeedback.current
    var pressedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tracedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var downCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun cellOf(x: Float, y: Float, sidePx: Float): Pair<Int, Int> {
        // Edge-clamped resolution: a drag leaving the board ends on a boundary cell.
        val col = (x / sidePx * model.n).toInt().coerceIn(0, model.n - 1)
        val row = (y / sidePx * model.n).toInt().coerceIn(0, model.n - 1)
        return row to col
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(model.n) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        pressedCell = null
                    },
                    onTap = { offset ->
                        val (row, col) = cellOf(offset.x, offset.y, size.width.toFloat())
                        pressedCell = null
                        state.onTap(row * model.n + col)
                        // §5.2: queen placement and removal carry the heavier haptic.
                        if (state.lastPlacedQueen != null || state.lastRemovedQueen != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                )
            }
            .pointerInput(model.n) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    downCell = cellOf(down.position.x, down.position.y, size.width.toFloat())
                    pressedCell = downCell
                }
            }
            .pointerInput(model.n) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val cell = downCell ?: cellOf(offset.x, offset.y, size.width.toFloat())
                        tracedCell = cell
                        pressedCell = cell
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        state.onDragCell(cell.first * model.n + cell.second)
                    },
                    onDrag = { change, _ ->
                        val cell = cellOf(change.position.x, change.position.y, size.width.toFloat())
                        if (cell != tracedCell) {
                            tracedCell = cell
                            pressedCell = cell
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            state.onDragCell(cell.first * model.n + cell.second)
                        }
                    },
                    onDragEnd = {
                        tracedCell = null
                        pressedCell = null
                    },
                    onDragCancel = {
                        tracedCell = null
                        pressedCell = null
                    },
                )
            },
    ) {
        BoardGrid(
            rows = model.n,
            cols = model.n,
            modifier = Modifier.matchParentSize(),
            cellContent = { row, col ->
                QueensCell(
                    model = model,
                    idx = row * model.n + col,
                    variants = variants,
                )
            },
        )
        // §3.1 cell-edge layer (F-1): the region fills are opaque and sit ABOVE
        // BoardGrid's under-strokes, so cell boundaries must be drawn over the fills —
        // a hairline lattice on every cell edge, heavier ink on region boundaries, and
        // the §5.5 hinted-region boundary on top. Glyphs never reach cell edges, so
        // the marks stay visually above this layer.
        val edgeInk = MaterialTheme.colorScheme.onSurface
        Canvas(Modifier.matchParentSize()) {
            drawCellEdges(model, boardTokens.boardSelection, edgeInk)
        }
        // §3.1: instant pressed-cell highlight — touch-down feedback precedes commit.
        val pressHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        pressedCell?.let { (row, col) ->
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val g = GridGeometry(model.n, model.n, size.width, size.height)
                        drawRect(
                            pressHighlight,
                            topLeft = Offset(col * g.cellWidth, row * g.cellHeight),
                            size = Size(g.cellWidth, g.cellHeight),
                        )
                    },
            )
        }
    }
}

/**
 * §3.1 edge layer (F-1), drawn above the opaque region fills: a [QueensBoardDefaults.cellGridLineWeight]
 * hairline on every cell edge (the lattice plus the board outline) in low-alpha ink,
 * a full-contrast [QueensBoardDefaults.regionBorderWeight] stroke on every interior edge
 * between two different regions so region shapes read, and the hinted region's boundary
 * in [QueensBoardDefaults.hintBorderWeight] boardSelection (§5.5) on top of that.
 */
private fun DrawScope.drawCellEdges(model: QueensBoardModel, hintColor: Color, ink: Color) {
    val n = model.n
    val g = GridGeometry(n, n, size.width, size.height)
    val hairline = QueensBoardDefaults.cellGridLineWeight.toPx()
    val regionWeight = QueensBoardDefaults.regionBorderWeight.toPx()
    val hintWeight = QueensBoardDefaults.hintBorderWeight.toPx()
    val faint = ink.copy(alpha = QueensBoardDefaults.cellGridLineAlpha)

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Color, width: Float) {
        drawLine(color, Offset(x0, y0), Offset(x1, y1), strokeWidth = width)
    }
    // Hairline lattice: every interior gridline, exactly once.
    for (i in 1 until n) {
        line(i * g.cellWidth, 0f, i * g.cellWidth, size.height, faint, hairline)
        line(0f, i * g.cellHeight, size.width, i * g.cellHeight, faint, hairline)
    }
    // Board outline, inset half a stroke so nothing clips outside the canvas.
    drawRect(
        faint,
        topLeft = Offset(hairline / 2f, hairline / 2f),
        size = Size(size.width - hairline, size.height - hairline),
        style = Stroke(width = hairline),
    )
    // Interior edges toward a different region: region boundary at full-contrast ink
    // (each interior edge visited once — right/down neighbors only), upgraded to the
    // §5.5 hint boundary when it separates the hinted region from another.
    val hintCells = model.hintCells
    for (row in 0 until n) for (col in 0 until n) {
        val idx = row * n + col
        val region = model.regions[idx]
        val hinted = idx in hintCells && model.hintRegion != null
        if (col + 1 < n && model.regions[idx + 1] != region) {
            val otherHinted = idx + 1 in hintCells && model.hintRegion != null
            val x = (col + 1) * g.cellWidth
            line(x, row * g.cellHeight, x, (row + 1) * g.cellHeight,
                if (hinted || otherHinted) hintColor else ink,
                if (hinted || otherHinted) hintWeight else regionWeight,
            )
        }
        if (row + 1 < n && model.regions[idx + n] != region) {
            val otherHinted = idx + n in hintCells && model.hintRegion != null
            val y = (row + 1) * g.cellHeight
            line(col * g.cellWidth, y, (col + 1) * g.cellWidth, y,
                if (hinted || otherHinted) hintColor else ink,
                if (hinted || otherHinted) hintWeight else regionWeight,
            )
        }
    }
}

/**
 * One cell: region fill, the state glyph with its §3 entrance/exit motion, the §5.5
 * hint overlay + pulse + stage-2 crown ghost, and the A14 violation cue. All animation
 * runs through one presence Animatable per cell — reduced motion collapses every
 * transition to an instant state change.
 */
@Composable
private fun QueensCell(
    model: QueensBoardModel,
    idx: Int,
    variants: QueensAbVariants,
) {
    val n = model.n
    val dark = currentIsDarkTheme()
    val fill = QueensRegionPalette.fill(model.regions[idx], dark, variants.paletteDensity)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val scrim = MaterialTheme.colorScheme.scrim
    val ink = remember(model.regions[idx], dark) {
        QueensRegionPalette.onInkFor(fill, onSurface, scrim)
    }
    val boardTokens = LocalBoardColors.current
    val error = MaterialTheme.colorScheme.error
    val reducedMotion = LocalQueensReducedMotion.current
    val visual = model.visuals[idx]
    var shown by remember { mutableStateOf(visual) }

    // Presence drives glyph alpha; scale is derived per-visual (§3.2 manual scale-in,
    // §3.3 auto fade-only, queen pop §3.4). Exits animate OUT then swap `shown`.
    val presence = remember { Animatable(1f) }
    LaunchedEffect(visual) {
        when {
            reducedMotion -> {
                presence.snapTo(1f)
                shown = visual
            }
            visual == shown -> presence.snapTo(1f)
            visual == QueensCellVisual.EMPTY -> {
                // Exit: fade out, faster than enter (§3.3); AUTO mass-exit staggers
                // 10ms/cell capped at 80ms so a wave reads as one orchestration.
                val stagger = if (shown == QueensCellVisual.X_AUTO) {
                    ((idx % 8) * QueensBoardDefaults.autoMarkStaggerStepMillis)
                        .coerceAtMost(QueensBoardDefaults.autoMarkStaggerCapMillis)
                } else {
                    0L
                }
                kotlinx.coroutines.delay(stagger)
                presence.animateTo(
                    0f,
                    tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedAccelerate),
                )
                shown = visual
            }
            else -> {
                shown = visual
                presence.snapTo(0f)
                presence.animateTo(
                    1f,
                    tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
                )
            }
        }
    }

    // §5.5 hint pulse: two DurationExpressive cycles, then rests at the static overlay.
    val pulse = remember(model.hintRegion) { Animatable(1f) }
    LaunchedEffect(model.hintRegion) {
        pulse.snapTo(1f)
        if (model.hintRegion != null && !reducedMotion) {
            repeat(QueensBoardDefaults.hintPulseCycles) {
                pulse.animateTo(0.4f, tween(MotionTokens.DurationExpressive, easing = MotionTokens.EasingStandard))
                pulse.animateTo(1f, tween(MotionTokens.DurationExpressive, easing = MotionTokens.EasingStandard))
            }
        }
    }
    val hinted = model.hintCells.contains(idx)
    // Stage 2 (fix round 6): the pointed forced cell — brighter emphasis + crown ghost.
    val pointed = model.hintCell == idx
    val violated = model.violations.contains(idx)

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(fill) },
        ) {
            val p = presence.value
            // A14 violation cue: stripes render FIRST — fill, then pattern, then the
            // glyph (§3.5 "glyphs stay readable above the pattern"). Two correctness
            // fixes (F-3/F-5, fix round 3): the stripe lines span -height..width+height
            // to cover the 45° corners, and Compose does NOT clip draws to layout
            // bounds — unclipped, every violated cell painted stripe wedges INTO its
            // neighbors, covering the neighbor's crown/X (the "queen no longer
            // visible" report). clipRect confines the pattern to this cell.
            if (violated) {
                clipRect(0f, 0f, size.width, size.height) {
                    when (variants.violationCue) {
                        ViolationCue.STRIPES_SCRIM -> drawViolationStripes(
                            error = error,
                            scrim = scrim,
                            pitch = QueensBoardDefaults.violationStripePitch.toPx(),
                            band = QueensBoardDefaults.violationStripeWidth.toPx(),
                            scrimBand = QueensBoardDefaults.violationScrimWidth.toPx(),
                        )
                        ViolationCue.TINT -> if (QueensRegionPalette.cielL(fill) >= 55f) {
                            drawRect(error.copy(alpha = QueensBoardDefaults.violationTintAlpha))
                        } else {
                            drawViolationStripes(
                                error = error,
                                scrim = scrim,
                                pitch = QueensBoardDefaults.violationStripePitch.toPx(),
                                band = QueensBoardDefaults.violationStripeWidth.toPx(),
                                scrimBand = QueensBoardDefaults.violationScrimWidth.toPx(),
                            )
                        }
                    }
                }
            }
            when (shown) {
                QueensCellVisual.X_MANUAL -> drawX(
                    ink, QueensBoardDefaults.xManualWeight.toPx(),
                    QueensBoardDefaults.xManualLengthFraction,
                    scale = 0.7f + 0.3f * p, alpha = p,
                )
                QueensCellVisual.X_AUTO -> drawX(
                    ink, QueensBoardDefaults.xManualWeight.toPx(),
                    QueensBoardDefaults.xManualLengthFraction,
                    alpha = p,
                )
                QueensCellVisual.QUEEN -> drawQueen(ink, QueensBoardDefaults.queenHeightFraction, alpha = p)
                QueensCellVisual.EMPTY -> Unit
            }
        }
        if (hinted) {
            val emphasis = if (pointed) QueensBoardDefaults.hintPointedAlphaBoost else 1f
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            boardTokens.boardSelection.copy(
                                alpha = (QueensBoardDefaults.hintOverlayAlpha * pulse.value * emphasis)
                                    .coerceAtMost(1f),
                            ),
                        )
                    },
            )
        }
        if (pointed && shown == QueensCellVisual.EMPTY) {
            // §5.5 stage 2: the crown ghost — the forced cell's silhouette at reduced
            // alpha, above the emphasized overlay. "This cell, exactly."
            Canvas(Modifier.fillMaxSize()) {
                drawQueen(ink, QueensBoardDefaults.queenHeightFraction, alpha = QueensBoardDefaults.hintGhostAlpha)
            }
        }
    }
}

private fun DrawScope.drawX(color: Color, weightPx: Float, lengthFraction: Float, scale: Float = 1f, alpha: Float = 1f) {
    val length = size.minDimension * lengthFraction * scale
    val half = length / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawLine(color, Offset(cx - half, cy - half), Offset(cx + half, cy + half), weightPx * scale, StrokeCap.Round, alpha = alpha)
    drawLine(color, Offset(cx - half, cy + half), Offset(cx + half, cy - half), weightPx * scale, StrokeCap.Round, alpha = alpha)
}

/** Crown silhouette, single path: three points + band base, height = fraction of cell. */
private fun DrawScope.drawQueen(color: Color, heightFraction: Float, alpha: Float) {
    val h = size.minDimension * heightFraction
    val w = h * 1.15f
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    val band = h * 0.16f
    val path = Path().apply {
        moveTo(left, top + h * 0.62f) // left base of crown body
        lineTo(left, top + h * 0.18f)
        lineTo(left + w * 0.26f, top + h * 0.42f) // left valley
        lineTo(left + w * 0.5f, top) // center peak
        lineTo(left + w * 0.74f, top + h * 0.42f) // right valley
        lineTo(left + w, top + h * 0.18f)
        lineTo(left + w, top + h * 0.62f)
        close()
        addRect(androidx.compose.ui.geometry.Rect(left, top + h * 0.70f, left + w, top + h * 0.70f + band))
    }
    drawPath(path, color.copy(alpha = alpha))
}

/** §3.5: 45° diagonal bands — a 2dp scrim band under each 3dp error band, 6dp pitch.
 *  Black under red guarantees the composite clears 3:1 against any fill lightness. */
private fun DrawScope.drawViolationStripes(error: Color, scrim: Color, pitch: Float, band: Float, scrimBand: Float) {
    val diagonal = size.width + size.height
    var start = -size.height
    while (start < diagonal) {
        drawLine(scrim, Offset(start, size.height), Offset(start + size.height, 0f), strokeWidth = band + scrimBand)
        drawLine(error, Offset(start + scrimBand / 2f, size.height), Offset(start + size.height + scrimBand / 2f, 0f), strokeWidth = band)
        start += pitch
    }
}

@Composable
private fun currentIsDarkTheme(): Boolean {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return androidx.compose.runtime.remember(configuration) {
        (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

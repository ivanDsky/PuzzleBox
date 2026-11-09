package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import com.ivandsky.puzzlebox.designsystem.MotionTokens

/**
 * One cell: region fill, the state glyph with its §3 entrance/exit motion, the §5.5
 * hint overlay + pulse + stage-2 crown ghost, the guided lesson's answer ghost and stray
 * badge (spec A12), and the A14 violation cue. Per-cell entrances run through one
 * presence Animatable; the board-wide A13 effects (Pop, Pulse, Shake, the solve
 * orchestration) are read from [LocalQueensMotion] while drawing. Auto crosses that a
 * placement at [staggerFrom] adds grow in ring by ring outward from it. Reduced motion
 * collapses every transition to an instant state change.
 */
@Composable
internal fun QueensCell(
    model: QueensBoardModel,
    idx: Int,
    staggerFrom: Int? = null,
) {
    val n = model.n
    val dark = LocalDarkTheme.current
    val fill = QueensRegionPalette.fill(model.regions[idx], dark)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val scrim = MaterialTheme.colorScheme.scrim
    val ink = remember(model.regions[idx], dark) {
        QueensRegionPalette.onInkFor(fill, onSurface, scrim)
    }
    val boardTokens = LocalBoardColors.current
    val error = MaterialTheme.colorScheme.error
    val reducedMotion = LocalQueensReducedMotion.current
    val motion = LocalQueensMotion.current
    val washPeak = if (dark) QueensMotion.WASH_PEAK_DARK else QueensMotion.WASH_PEAK_LIGHT
    val visual = model.visuals[idx]
    var shown by remember { mutableStateOf(visual) }
    // An auto cross that Staggers in grows as it fades; any other auto cross only fades.
    var grows by remember { mutableStateOf(false) }

    // Presence drives glyph alpha; scale is derived per-visual (§3.2 manual scale-in,
    // §3.3 auto fade, A13 auto Stagger). A tapped queen's entrance is its Pop, drawn from
    // [motion]. Exits animate OUT then swap `shown`.
    val presence = remember { Animatable(1f) }
    // Scratch for the crown outline, rewound by every draw so the draw pass allocates nothing.
    val crownPath = remember { Path() }
    LaunchedEffect(visual) {
        when {
            reducedMotion -> {
                presence.snapTo(1f)
                shown = visual
            }
            visual == shown -> presence.snapTo(1f)
            visual == QueensCellVisual.EMPTY -> {
                // Exit: fade out, faster than enter (§3.3); AUTO mass-exit cascades
                // per cell up to a cap so a wave reads as one orchestration.
                val stagger = if (shown == QueensCellVisual.X_AUTO) {
                    ((idx % 8) * MotionTokens.CascadeStepMillis)
                        .coerceAtMost(MotionTokens.CascadeCapMillis)
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
            visual == QueensCellVisual.QUEEN && idx == staggerFrom && motion != null -> {
                shown = visual
                presence.snapTo(1f)
            }
            visual == QueensCellVisual.X_AUTO && staggerFrom != null -> {
                shown = visual
                grows = true
                presence.snapTo(0f)
                kotlinx.coroutines.delay(QueensMotion.staggerDelayMs(idx, staggerFrom, n))
                presence.animateTo(
                    1f,
                    tween(QueensMotion.STAGGER_ENTER_MS, easing = MotionTokens.EasingEmphasizedDecelerate),
                )
            }
            else -> {
                shown = visual
                grows = false
                presence.snapTo(0f)
                presence.animateTo(
                    1f,
                    tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
                )
            }
        }
    }

    // §5.5 hint pulse: two HintPulse cycles, then rests at the static overlay.
    val pulse = remember(model.hintRegion) { Animatable(1f) }
    LaunchedEffect(model.hintRegion) {
        pulse.snapTo(1f)
        if (model.hintRegion != null && !reducedMotion) {
            repeat(QueensBoardDefaults.hintPulseCycles) {
                pulse.animateTo(0.4f, tween(MotionTokens.HintPulseDuration, easing = MotionTokens.EasingStandard))
                pulse.animateTo(1f, tween(MotionTokens.HintPulseDuration, easing = MotionTokens.EasingStandard))
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
                    drawViolationStripes(
                        error = error,
                        scrim = scrim,
                        pitch = QueensBoardDefaults.violationStripePitch.toPx(),
                        band = QueensBoardDefaults.violationStripeWidth.toPx(),
                        scrimBand = QueensBoardDefaults.violationScrimWidth.toPx(),
                    )
                }
            }
            val wash = motion?.washOf(idx) ?: 0f
            if (wash > 0f) drawRect(QueensBoardDefaults.pulseWash, alpha = wash * washPeak)
            val cross = motion?.crossOf(idx) ?: 1f
            when (shown) {
                QueensCellVisual.X_MANUAL -> drawX(
                    ink, QueensBoardDefaults.crossWeight.toPx(),
                    QueensBoardDefaults.crossLengthFraction,
                    scale = (0.7f + 0.3f * p) * QueensMotion.solveCrossScale(cross), alpha = p * cross,
                )
                QueensCellVisual.X_AUTO -> drawX(
                    ink, QueensBoardDefaults.crossWeight.toPx(),
                    QueensBoardDefaults.crossLengthFraction,
                    scale = (if (grows) QueensMotion.STAGGER_FROM + (1f - QueensMotion.STAGGER_FROM) * p else 1f) *
                        QueensMotion.solveCrossScale(cross),
                    alpha = p * cross,
                )
                QueensCellVisual.QUEEN -> {
                    val scale = motion?.scaleOf(idx) ?: 1f
                    val shake = (motion?.shakeOf(idx) ?: 0f).dp.toPx()
                    withTransform({
                        translate(left = shake)
                        scale(scale, scale)
                    }) {
                        drawQueen(ink, QueensBoardDefaults.queenHeightFraction, alpha = p * (motion?.alphaOf(idx) ?: 1f), path = crownPath)
                    }
                }
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
        // §5.5 stage 2 and the guided lesson (spec A12) share the crown ghost — a dashed
        // outline of the queen, above the emphasized overlay: "a queen goes here", never
        // mistaken for a placed (solid) queen. A guided ghost stays over an × until the
        // queen itself lands.
        val guided = idx in model.ghosts && shown != QueensCellVisual.QUEEN
        if ((pointed && shown == QueensCellVisual.EMPTY) || guided) {
            val ghost = if (guided) {
                Modifier
                    .semantics { contentDescription = GHOST_DESCRIPTION }
                    .testTag("queens.ghost.$idx")
            } else {
                Modifier
            }
            Canvas(Modifier.fillMaxSize().then(ghost)) {
                val dash = QueensBoardDefaults.ghostDash.toPx()
                drawQueen(
                    ink, QueensBoardDefaults.queenHeightFraction,
                    alpha = QueensBoardDefaults.ghostOutlineAlpha,
                    path = crownPath,
                    style = Stroke(
                        width = QueensBoardDefaults.ghostStrokeWeight.toPx(),
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.6f)),
                    ),
                )
            }
        }
        // Guided lesson: a queen off the answer gets a faint corner × — calm, not a rule
        // mark — so the child sees which queen to tap away.
        if (idx in model.strays && shown == QueensCellVisual.QUEEN) {
            Canvas(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxSize(QueensBoardDefaults.strayBadgeFraction)
                    .semantics { contentDescription = STRAY_DESCRIPTION }
                    .testTag("queens.stray.$idx"),
            ) {
                drawX(
                    ink, QueensBoardDefaults.strayBadgeWeight.toPx(),
                    QueensBoardDefaults.strayBadgeLengthFraction,
                    alpha = QueensBoardDefaults.hintGhostAlpha,
                )
            }
        }
    }
}

/** TalkBack's line for a guided lesson's answer ghost (spec A12). */
private const val GHOST_DESCRIPTION = "A queen goes here"

/** TalkBack's line for a guided lesson's queen off the answer. */
private const val STRAY_DESCRIPTION = "No queen here. Tap to take it away."

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens

/**
 * One cell: region fill, the state glyph with its §3 entrance/exit motion, the §5.5
 * hint overlay + pulse + stage-2 crown ghost, the guided lesson's answer ghost (spec
 * A12), and the A14 violation cue. All animation runs through one presence Animatable
 * per cell — reduced motion collapses every transition to an instant state change.
 */
@Composable
internal fun QueensCell(
    model: QueensBoardModel,
    idx: Int,
) {
    val n = model.n
    val dark = currentIsDarkTheme()
    val fill = QueensRegionPalette.fill(model.regions[idx], dark)
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
                    drawViolationStripes(
                        error = error,
                        scrim = scrim,
                        pitch = QueensBoardDefaults.violationStripePitch.toPx(),
                        band = QueensBoardDefaults.violationStripeWidth.toPx(),
                        scrimBand = QueensBoardDefaults.violationScrimWidth.toPx(),
                    )
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
        // §5.5 stage 2 and the guided lesson (spec A12) share the crown ghost — the
        // queen's silhouette at reduced alpha, above the emphasized overlay. "This cell,
        // exactly." A guided ghost stays over an × until the queen itself lands.
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
                drawQueen(ink, QueensBoardDefaults.queenHeightFraction, alpha = QueensBoardDefaults.hintGhostAlpha)
            }
        }
    }
}

/** TalkBack's line for a guided lesson's answer ghost (spec A12). */
private const val GHOST_DESCRIPTION = "A queen goes here"

@Composable
private fun currentIsDarkTheme(): Boolean {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return androidx.compose.runtime.remember(configuration) {
        (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

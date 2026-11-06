package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.editor.QueensAnswerFit
import com.ivandsky.puzzlebox.games.queens.editor.QueensContiguity
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queensui.QueensBoardDefaults
import com.ivandsky.puzzlebox.games.queensui.drawViolationStripes
import kotlinx.coroutines.delay

/**
 * What the editor board flags for the author (prototype 03 b, spec S19): [diff] are the cells
 * two solutions disagree on (a Multiple verdict), [clash] the cells that break a rule (None),
 * [split] the stray pieces of regions in more than one piece, [ghosts] the other answer's
 * queens when the author has crowns (40).
 */
internal data class QueensDiagnostics(val diff: Set<Int>, val clash: Set<Int>, val split: Set<Int>, val ghosts: Set<Int>)

/**
 * Diagnostics for [board] under [verdict]. Diff and clash follow the verdict at once. A stray
 * piece is striped only after the board has held still for [MotionTokens.SettleMillis], so
 * stripes don't flash mid-paint; a piece that is mended loses its stripes at once. When [fit]
 * is [QueensAnswerFit.Ambiguous] (crowns + 2 answers; the board passes the last settled
 * reading through Checking, as the coach line does) the other answer shows as [ghosts] and
 * the diff ring marks only its movable cells, where a recolour can kill that answer.
 */
@Composable
internal fun rememberQueensDiagnostics(verdict: Verdict, board: QueensDraft, fit: QueensAnswerFit): QueensDiagnostics {
    val strays = remember(board) { strayCells(board) }
    // A preview has no clock to wait out the pause on, so it shows the settled stripes.
    val still = LocalInspectionMode.current
    var settled by remember { mutableStateOf(if (still) strays else emptySet()) }
    LaunchedEffect(board) {
        settled = settled intersect strays
        delay(MotionTokens.SettleMillis)
        settled = strays
    }
    val ambiguous = fit as? QueensAnswerFit.Ambiguous
    return QueensDiagnostics(
        diff = ambiguous?.movable ?: (verdict as? Verdict.Multiple)?.differingCells.orEmpty(),
        clash = (verdict as? Verdict.None)?.conflicts.orEmpty(),
        split = settled intersect strays,
        ghosts = ambiguous?.ghosts.orEmpty(),
    )
}

/**
 * Cell [idx]'s marks, drawn over its fill: the split stripes (the play UI's violation
 * stripes, which appear after a delay rather than animate), then the clash fill, the
 * diff ring and the ghost crown ([QueensGhostLayer]), each fading in. Each mark carries
 * `queens.editor.cell.<idx>.<split|clash|diff|ghost>`.
 */
@Composable
internal fun QueensCellDiagnostics(idx: Int, diagnostics: QueensDiagnostics, reducedMotion: Boolean) {
    val tokens = LocalBoardColors.current
    if (idx in diagnostics.split) {
        val scrim = MaterialTheme.colorScheme.scrim
        Canvas(Modifier.fillMaxSize().testTag("queens.editor.cell.$idx.split")) {
            clipRect(0f, 0f, size.width, size.height) {
                drawViolationStripes(
                    error = tokens.editorSplitHatch,
                    scrim = scrim,
                    pitch = QueensBoardDefaults.violationStripePitch.toPx(),
                    band = QueensBoardDefaults.violationStripeWidth.toPx(),
                    scrimBand = QueensBoardDefaults.violationScrimWidth.toPx(),
                )
            }
        }
    }
    if (idx in diagnostics.clash) {
        FadingMark("queens.editor.cell.$idx.clash", reducedMotion) { drawRect(tokens.editorClashFill) }
    }
    if (idx in diagnostics.diff) {
        FadingMark("queens.editor.cell.$idx.diff", reducedMotion) { drawInsetRing(tokens.editorDiffRing) }
    }
    QueensGhostLayer(idx, idx in diagnostics.ghosts, reducedMotion)
}

/**
 * A cell-sized mark that **fades in** when it first shows (spec S19); reduced motion keeps the
 * short fade. A preview has no frames to run, so it shows the mark settled.
 */
@Composable
internal fun FadingMark(tag: String, reducedMotion: Boolean, draw: DrawScope.() -> Unit) {
    val still = LocalInspectionMode.current
    var shown by remember { mutableStateOf(still) }
    LaunchedEffect(Unit) { shown = true }
    val millis = if (reducedMotion) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(millis, easing = MotionTokens.EasingEmphasizedDecelerate),
        label = "diagnosticFade",
    )
    Canvas(Modifier.fillMaxSize().testTag(tag).graphicsLayer { this.alpha = alpha }, onDraw = draw)
}

/** The diff ring: a stroke inset inside the cell, the selection outline's weight. */
private fun DrawScope.drawInsetRing(color: Color) {
    val weight = QueensEditorDefaults.selectionStrokeWeight.toPx()
    drawRect(
        color = color,
        topLeft = Offset(weight / 2, weight / 2),
        size = Size(size.width - weight, size.height - weight),
        style = Stroke(weight),
    )
}

/** Every cell of a split region outside its largest piece (the first of equal pieces stays). */
private fun strayCells(board: QueensDraft): Set<Int> =
    QueensContiguity.splitRegions(board).values.flatMapTo(sortedSetOf()) { pieces ->
        val kept = pieces.maxBy { it.size }
        pieces.filter { it !== kept }.flatten()
    }

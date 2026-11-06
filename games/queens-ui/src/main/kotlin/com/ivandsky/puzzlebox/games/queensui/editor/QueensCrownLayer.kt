package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queensui.QueensBoardDefaults
import com.ivandsky.puzzlebox.games.queensui.QueensRegionPalette
import com.ivandsky.puzzlebox.games.queensui.drawQueen
import com.ivandsky.puzzlebox.games.queensui.drawX
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** One refused crown tap on [cell]; [seq] numbers it, so a repeat on the same cell jolts again. */
internal data class QueensRejection(val cell: Int, val seq: Int)

/**
 * The latest refused crown tap from [controller], held for [MotionTokens.RejectHoldMillis]
 * then cleared. Each refusal plays exactly one reject haptic.
 */
@Composable
internal fun rememberQueensRejection(controller: QueensEditorController): QueensRejection? {
    val haptics = LocalHapticFeedback.current
    var rejection by remember(controller) { mutableStateOf<QueensRejection?>(null) }
    LaunchedEffect(controller) {
        var seq = 0
        controller.rejected.collectLatest { cell ->
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            rejection = QueensRejection(cell, ++seq)
            delay(MotionTokens.RejectHoldMillis)
            rejection = null
        }
    }
    return rejection
}

/**
 * Cell [idx]'s answer-crown layer over its fill: the play board's crown when [crowned]
 * (tag `queens.editor.cell.<idx>.crown`), else its automatic × when [crossed] (tag
 * `.autoX`), both in the region's on-ink. While [rejection] points here the cell wears the
 * error wash and plays the Reject jolt (a medium-bouncy spring dip and back); reduced
 * motion keeps the wash without the jolt. Draws nothing on a plain cell.
 */
@Composable
internal fun QueensCrownLayer(
    idx: Int,
    region: Int,
    crowned: Boolean,
    crossed: Boolean,
    rejection: QueensRejection?,
    reducedMotion: Boolean,
) {
    if (!crowned && !crossed) return
    val dark = isSystemInDarkTheme()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val scrim = MaterialTheme.colorScheme.scrim
    val ink = remember(region, dark, onSurface, scrim) {
        if (region == QueensDraft.UNPAINTED) onSurface else QueensRegionPalette.onInkFor(QueensRegionPalette.fill(region, dark), onSurface, scrim)
    }
    val wash = MaterialTheme.colorScheme.errorContainer.copy(alpha = REJECT_WASH_ALPHA)
    val mine = rejection?.takeIf { it.cell == idx }
    val jolt = remember { Animatable(1f) }
    LaunchedEffect(mine?.seq) {
        val bounce = spring<Float>(MotionTokens.RejectSpringDamping, MotionTokens.RejectSpringStiffness)
        if (mine != null && !reducedMotion) jolt.animateTo(REJECT_SCALE, bounce)
        jolt.animateTo(1f, bounce)
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .testTag(if (crowned) "queens.editor.cell.$idx.crown" else "queens.editor.cell.$idx.autoX")
            .graphicsLayer {
                scaleX = jolt.value
                scaleY = jolt.value
            },
    ) {
        if (mine != null) drawRect(wash)
        if (crowned) {
            drawQueen(ink, QueensBoardDefaults.queenHeightFraction, alpha = 1f)
        } else {
            drawX(ink, QueensBoardDefaults.xManualWeight.toPx(), QueensBoardDefaults.xManualLengthFraction)
        }
    }
}

/** The jolt's dip, as Zip's rejected cell. */
private const val REJECT_SCALE = 0.86f

/** The refused cell's error wash, as Zip's rejected cell. */
private const val REJECT_WASH_ALPHA = 0.7f

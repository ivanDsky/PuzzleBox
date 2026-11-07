package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay

private const val CardEnterScale = 0.9f

/**
 * The single-pane bar's verdict pill ([compactPill]: glyph only) and, while [cardOpen], its card:
 * a half-expanded bottom sheet when [asSheet] (a phone, so the board stays visible above it),
 * otherwise a popover over the pill. Both close through [onDismiss].
 */
@Composable
internal fun PillWithCard(
    verdict: Verdict,
    compactPill: Boolean,
    asSheet: Boolean,
    cardOpen: Boolean,
    overlay: EditorOverlay,
    onShow: () -> Unit,
    onDismiss: () -> Unit,
    onCheckAgain: () -> Unit,
    onOverlay: (EditorOverlay) -> Unit,
    issues: @Composable (Modifier) -> Unit,
) {
    Box {
        VerdictPill(verdict = verdict, onClick = onShow, compact = compactPill)
        if (!cardOpen) return@Box
        val card: @Composable (Modifier) -> Unit = { cardModifier ->
            VerdictCard(
                verdict = verdict,
                onCheckAgain = onCheckAgain,
                overlay = overlay,
                onOverlay = onOverlay,
                issues = issues,
                modifier = cardModifier,
            )
        }
        if (asSheet) VerdictCardSheet(onDismiss, card) else VerdictCardPopover(onDismiss, card)
    }
}

/** The card in a sheet that opens half-expanded; dragging it up shows the rest of a long fix list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerdictCardSheet(onDismiss: () -> Unit, card: @Composable (Modifier) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("create.verdict.sheet"),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            card(Modifier.fillMaxWidth())
        }
    }
}

/**
 * The card over the pill, its top end on the pill's and kept [Spacing.lg] inside the window, so
 * a narrow window never cuts it: an outside tap or Back dismisses it, instantly. Motion:
 * origin-aware **Scale in** from 0.9 + **Fade in** at the top-right corner, MotionTokens medium,
 * emphasized decelerate; under reduced motion a plain 120 ms fade.
 */
@Composable
private fun VerdictCardPopover(onDismiss: () -> Unit, card: @Composable (Modifier) -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val margin = with(LocalDensity.current) { Spacing.lg.roundToPx() }
    Popup(
        popupPositionProvider = remember(margin) { WindowClamped.overTopEnd(margin) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        BoxWithConstraints {
            val fits = Modifier.widthIn(max = maxWidth - Spacing.lg * 2)
            AnimatedVisibility(
                visibleState = shown,
                enter = if (reducedMotion) {
                    fadeIn(tween(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingStandard))
                } else {
                    val spec = tween<Float>(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate)
                    scaleIn(spec, CardEnterScale, TransformOrigin(1f, 0f)) + fadeIn(spec)
                },
            ) {
                card(fits)
            }
        }
    }
}

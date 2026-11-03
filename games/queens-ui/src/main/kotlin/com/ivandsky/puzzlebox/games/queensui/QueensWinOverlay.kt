package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import kotlin.math.roundToInt

/**
 * §5.6 win orchestration — presented as a TRUE MODAL (fix round 5, defect A): a
 * fullscreen dialog in its own window. The screen-level Box of fix round 4 still left
 * the shell's bottom nav un-dimmed and interactive — tapping Profile mid-celebration
 * navigated away and silently abandoned the win state. Inside the dialog window
 * everything behind (board, side panel, bottom nav, system bars) is dimmed by the
 * platform dim + the §5.6 scrim, and inert; back and outside taps are blocked so the
 * win state cannot be backed out of mid-celebration — the banner's "Play again" is the
 * flow's only way forward. Sequence (inside the fullscreen dialog window):
 * 1. surface dims 8% via the scheme scrim (Fade, DurationMedium) so the state reads;
 * 2. the banner slides in from the bottom (24dp) + fade, DurationMedium,
 *    EasingEmphasizedDecelerate; score line is plain text — no number ticker (§5.6).
 * Reduced motion: static overlay, no slide.
 */
@Composable
fun QueensWinOverlay(
    state: QueensBoardState,
    modifier: Modifier = Modifier,
    onPlayAgain: () -> Unit = {},
) {
    val reducedMotion = rememberReducedMotion()
    val model = QueensBoardModel.of(state.session, state.hint, state.autoCheck)
    val scrimAlpha = remember { Animatable(if (reducedMotion) 0.08f else 0f) }
    val bannerProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(model.win) {
        if (!model.win) {
            scrimAlpha.snapTo(0f)
            bannerProgress.snapTo(0f)
            return@LaunchedEffect
        }
        if (!reducedMotion) {
            scrimAlpha.animateTo(0.08f, tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard))
            bannerProgress.animateTo(1f, tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate))
        } else {
            scrimAlpha.snapTo(0.08f)
            bannerProgress.snapTo(1f)
        }
    }
    if (!model.win) return
    val winScrim = MaterialTheme.colorScheme.scrim
    // Defect A (fix round 5): modal presentation semantics. Back and outside taps are
    // blocked (the win cannot be silently abandoned); the platform dim covers every
    // window behind, including the shell's bottom nav (undimmed/interactive before).
    Dialog(
        onDismissRequest = { /* blocked: the win state is never dismissed mid-celebration */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .testTag("queens/win_overlay")
                .drawBehind {
                    drawRect(winScrim.copy(alpha = scrimAlpha.value))
                },
        ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset {
                    IntOffset(0, ((1f - bannerProgress.value) * 24.dp.roundToPx()).roundToInt())
                }
                .background(MaterialTheme.colorScheme.surface.copy(alpha = bannerProgress.value))
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Solved!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${model.queens.size} queens · ${model.n}×${model.n}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            // F-3 (fix round 3): the win previously locked every control with no way
            // forward — the banner now carries the flow's replay as its forward action.
            Button(onClick = onPlayAgain) { Text("Play again") }
        }
        }
    }
}

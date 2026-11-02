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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import kotlin.math.roundToInt

/**
 * §5.6 win orchestration (the queens cascade itself runs per-cell in [QueensCell]):
 * 1. board dims 8% via the scheme scrim (Fade, DurationMedium) so the surface reads;
 * 2. the banner slides in from the bottom (24dp) + fade, DurationMedium,
 *    EasingEmphasizedDecelerate; score line is plain text — no number ticker (§5.6).
 * Reduced motion (provided at board level): static overlay, no slide.
 */
@Composable
internal fun QueensWinOverlay(model: QueensBoardModel, cascadeOrder: CascadeOrder) {
    val reducedMotion = LocalQueensReducedMotion.current
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
    Box(
        Modifier
            .fillMaxSize()
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
        }
    }
}

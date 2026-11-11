package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.NewBestBurst
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.rememberSolveSweepState
import com.ivandsky.puzzlebox.designsystem.solveSweep

private const val QuietFadeMs = 900
private val QuietBorder = 1.dp

/**
 * The frame both final-score layouts sit in. A win stamps in with a sweep and a burst, in the primary accent, with a
 * [winBorder]-wide outline; a loss or a draw fades in slowly in the outline colour with a thin border and no burst, so a
 * loss stays quiet. [content] is the card's insides.
 */
@Composable
internal fun SummaryFrame(win: Boolean, winBorder: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val sweep = rememberSolveSweepState()
    val fade = rememberQuietFade(enabled = !win)
    val inspecting = LocalInspectionMode.current
    var stamped by remember { mutableStateOf(inspecting) }
    LaunchedEffect(win) {
        stamped = true
        if (win) sweep.play()
    }
    Box(modifier.fillMaxWidth().testTag("reveal.summary.card")) {
        Stamp(visible = stamped || !win) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = scheme.surface,
                border = BorderStroke(if (win) winBorder else QuietBorder, if (win) scheme.primary else scheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = fade.value }
                    .solveSweep(sweep, scheme.primary, MaterialTheme.shapes.large),
                content = content,
            )
        }
        NewBestBurst(
            trigger = win,
            colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary),
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** Opacity of the quiet entrance: a slow fade from 0 (at once under reduced motion or in a preview); [enabled] false is already 1. */
@Composable
private fun rememberQuietFade(enabled: Boolean): Animatable<Float, *> {
    val instant = rememberReducedMotion() || LocalInspectionMode.current || !enabled
    val alpha = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(alpha) { alpha.animateTo(1f, tween(QuietFadeMs)) }
    return alpha
}

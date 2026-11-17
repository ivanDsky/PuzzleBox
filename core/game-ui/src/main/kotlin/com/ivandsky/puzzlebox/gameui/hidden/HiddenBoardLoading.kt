package com.ivandsky.puzzlebox.gameui.hidden

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/** On the bars only while they pulse. */
internal const val HIDDEN_BOARD_PULSE_TAG = "hidden-board.pulse"

private const val BARS = 3

/**
 * Holds the room of a [rows] by [cols] board while its puzzle loads, so the page does not jump when the board
 * arrives: three soft bars that dim and brighten. Still under reduced motion.
 */
@Composable
fun HiddenBoardLoading(modifier: Modifier = Modifier, rows: Int = 1, cols: Int = 1) {
    val still = rememberReducedMotion() || LocalInspectionMode.current
    Box(modifier.hiddenBoardBounds(rows, cols).semantics { contentDescription = "Loading board" }) {
        if (still) Bars(Modifier) else PulsingBars()
    }
}

@Composable
private fun PulsingBars() {
    val alpha by rememberInfiniteTransition(label = "hidden-board-pulse").animateFloat(
        initialValue = 1f,
        targetValue = HiddenBoardTokens.PulseFloor,
        animationSpec = infiniteRepeatable(
            tween(HiddenBoardTokens.PulseMillis / 2, easing = MotionTokens.EasingEase),
            RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Bars(Modifier.testTag(HIDDEN_BOARD_PULSE_TAG).graphicsLayer { this.alpha = alpha })
}

@Composable
private fun Bars(modifier: Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(BARS) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
    }
}

package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.beatDelayMs
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private val GapRise = 8.dp
private const val GapDurationMs = 220
private const val GapStaggerMs = 60
private const val RowEntranceMs = 300

/**
 * One puzzle row's gap to your first solve, in the slot a friend duel's basis line takes. It fades in and rises 8 dp
 * over 220 ms once row [index] has entered, [GapStaggerMs] later for each row before it. Reduced motion shows it at once.
 */
@Composable
internal fun ImprovementGap(
    improvement: Improvement,
    style: TextStyle,
    color: Color,
    index: Int,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    val settled = LocalInspectionMode.current
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (settled || reduced) 1f else 0f) }
    LaunchedEffect(progress, reduced) {
        if (reduced) return@LaunchedEffect
        val delay = beatDelayMs(ResultBeats.Blocks, index) + RowEntranceMs + index * GapStaggerMs
        progress.animateTo(1f, tween(GapDurationMs, delayMillis = delay, easing = MotionTokens.EasingStandard))
    }
    val rise = with(LocalDensity.current) { GapRise.toPx() }
    Text(
        text = improvement.text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * rise
            }
            .testTag("reveal.gap.$index"),
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ImprovementGapPreview() = PuzzleBoxTheme {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val style = MaterialTheme.typography.labelMedium
        val color = MaterialTheme.colorScheme.primary
        ImprovementGap(Improvement(Trend.Quicker, 14), style, color, 0)
        ImprovementGap(Improvement(Trend.Slower, 7), style, color, 1)
        ImprovementGap(Improvement(Trend.Same, 0), style, color, 2)
    }
}

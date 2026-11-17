package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private const val LineFadeMs = 200

/** The score's own fade, which the line follows. */
private const val AfterScoreMs = 200

/**
 * "Faster on 2 of 3 · 0:41 quicker overall" under the verdict of a self duel: it fades in over 200 ms, linear, once the
 * score has. Reduced motion shows it at once.
 */
@Composable
internal fun ImprovementSummaryLine(text: String, modifier: Modifier = Modifier, centred: Boolean = true) {
    val settled = LocalInspectionMode.current
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (settled || reduced) 1f else 0f) }
    LaunchedEffect(progress, reduced) {
        if (reduced) return@LaunchedEffect
        progress.animateTo(1f, tween(LineFadeMs, delayMillis = AfterScoreMs, easing = LinearEasing))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (centred) TextAlign.Center else TextAlign.Start,
        modifier = modifier.fillMaxWidth().alpha(progress.value).testTag("reveal.summary.improvement"),
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ImprovementSummaryLinePreview() = PuzzleBoxTheme {
    ImprovementSummaryLine(
        improvementSummary(listOf(Improvement(Trend.Quicker, 14), Improvement(Trend.Quicker, 42), Improvement(Trend.Slower, 15))).orEmpty(),
        Modifier.padding(Spacing.lg),
    )
}

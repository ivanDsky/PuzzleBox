package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalTimerFill
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * One segment per page, the first [filled] of them lit in the progress fill over a `surfaceVariant` track: where the
 * player is in the story, nothing else, so it is not tappable. A segment fills from the left as its card arrives and is
 * instant under reduced motion.
 */
@Composable
internal fun RecapProgressStrip(count: Int, filled: Int, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = LocalTimerFill.current
    Row(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Card ${filled.coerceAtLeast(1)} of $count" }
            .testTag("recap.progress"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(count) { index ->
            val lit = index < filled
            val fraction by animateFloatAsState(
                targetValue = if (lit) 1f else 0f,
                animationSpec = when {
                    reduced -> snap()
                    lit -> tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)
                    else -> tween(MotionTokens.DurationExit, easing = MotionTokens.EasingStandard)
                },
                label = "recap.segment",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(SegmentHeight)
                    .clip(CircleShape)
                    .background(track)
                    .testTag("recap.progress.$index"),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = fraction
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .background(fill),
                )
            }
        }
    }
}

private val SegmentHeight = 6.dp

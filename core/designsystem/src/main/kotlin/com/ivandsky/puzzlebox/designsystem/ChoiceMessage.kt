package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val MessageMinHeight = ButtonMinHeight
private val IconSize = 20.dp
private val DropDistance = 4.dp
private const val ENTER_MILLIS = 220

/**
 * The one line under a choice control: a lock and a quiet [text] for a blocked choice, an alert
 * and an error-ink [text] for a failure ([isError]), the latter with a "Try again" text button
 * when [onRetry] is given. It fades in and drops 4dp over 220ms (instant under reduced motion);
 * the error text is a live region so it is announced when it appears.
 */
@Composable
fun ChoiceMessage(text: String, isError: Boolean, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val reduced = rememberReducedMotion()
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) enter.snapTo(1f) else enter.animateTo(1f, tween(ENTER_MILLIS, easing = MotionTokens.EasingStandard))
    }
    val ink = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .heightIn(min = MessageMinHeight)
            .graphicsLayer {
                alpha = enter.value
                translationY = (enter.value - 1f) * DropDistance.toPx()
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (isError) PuzzleIcons.Info else PuzzleIcons.Lock, contentDescription = null, modifier = Modifier.size(IconSize), tint = ink)
        Text(
            text,
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (isError) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
            color = ink,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (isError && onRetry != null) AppTextButton("Try again", onRetry)
    }
}

package com.ivandsky.puzzlebox.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.SkeletonLine

private const val OFFLINE_ALPHA = 0.7f
private const val SKELETON_ALPHA = 0.16f
private val BadgeGap = 6.dp
private val StatusHeight = 16.dp
private val StatusIcon = 12.dp
private val StatusIconGap = 3.dp
private val StatusSkeletonWidth = 28.dp
private val StatusSkeletonHeight = 10.dp

/**
 * One game of today's set: ring, name, status, the whole column one click target. A null
 * status is the loading skeleton. [next] puts the name in primary text (the CTA's game).
 * Tapping is [onClick] in every state — an offline badge's tap is its retry. A status change
 * crossfades the ring and status line in place.
 */
@Composable
internal fun TodayBadge(
    row: HomeUiState.TodayRow,
    next: Boolean,
    size: TodayBadgeSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val status = row.status
    Column(
        modifier = modifier
            .pressScale(interaction, reducedMotion)
            .clip(MaterialTheme.shapes.medium)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .alpha(if (status == TodayStatus.Offline) OFFLINE_ALPHA else 1f)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BadgeGap),
    ) {
        BadgeCrossfade(status, reducedMotion) { shown ->
            if (shown == null) {
                Box(Modifier.size(size.ring)) {
                    SkeletonLine(1f, size.ring, skeletonColor())
                }
            } else {
                TodayBadgeRing(row.brand, shown, size)
            }
        }
        Text(
            text = row.displayName,
            style = if (size == TodayBadgeSize.Expanded) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            color = if (next) LocalPrimaryText.current else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.height(StatusHeight), contentAlignment = Alignment.Center) {
            BadgeCrossfade(status, reducedMotion) { shown -> BadgeStatus(shown, row.brand.accent.colors().accent) }
        }
    }
}

@Composable
private fun BadgeCrossfade(
    status: TodayStatus?,
    reducedMotion: Boolean,
    content: @Composable (TodayStatus?) -> Unit,
) {
    if (reducedMotion) {
        content(status)
    } else {
        Crossfade(
            targetState = status,
            animationSpec = tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
            label = "today-badge-status",
        ) { content(it) }
    }
}

@Composable
private fun BadgeStatus(status: TodayStatus?, accent: Color) {
    when (status) {
        null -> Box(Modifier.width(StatusSkeletonWidth)) {
            SkeletonLine(1f, StatusSkeletonHeight, skeletonColor())
        }
        TodayStatus.NotStarted -> StatusText("New")
        is TodayStatus.InProgress -> StatusText(formatDuration(status.elapsedMs))
        is TodayStatus.Solved -> StatusText(formatDuration(status.timeMs), PuzzleIcons.Check, accent)
        TodayStatus.Offline -> StatusText("Retry", PuzzleIcons.Retry)
    }
}

@Composable
private fun StatusText(text: String, icon: ImageVector? = null, iconTint: Color = Color.Unspecified) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StatusIconGap),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (iconTint == Color.Unspecified) LocalContentColor.current else iconTint,
                    modifier = Modifier.size(StatusIcon),
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium.tabular(), maxLines = 1)
        }
    }
}

/** Skeleton fill: the card's foreground at 16%, as the hub's skeletons read. */
@Composable
internal fun skeletonColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = SKELETON_ALPHA)

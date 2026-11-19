package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalMutedStatus
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.play.domain.FriendStatus

private val ChipHeight = 28.dp
private val ChipIcon = 16.dp

// A status change pops the chip in from a little smaller, on the stamp's spring.
private const val CHIP_POP_FROM_SCALE = 0.85f

/** A friend puzzle's status as a pill, tagged `library.friends.<code>.status.<tag>`; a change cross-fades (a pop-in unless [reduced]). */
@Composable
internal fun StatusChip(code: String, status: FriendStatus, reduced: Boolean) {
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(MotionTokens.ReducedFadeDuration)) togetherWith fadeOut(tween(MotionTokens.ReducedFadeDuration))
            } else {
                (
                    fadeIn(tween(MotionTokens.StampFadeDuration)) +
                        scaleIn(spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness), CHIP_POP_FROM_SCALE)
                    ) togetherWith fadeOut(tween(MotionTokens.StampFadeDuration))
            }
        },
        contentAlignment = Alignment.CenterStart,
        label = "friend-status",
    ) { shown ->
        val look = chipLook(shown)
        Surface(
            shape = PillShape,
            color = look.container,
            contentColor = look.content,
            modifier = Modifier.height(ChipHeight).testTag("library.friends.$code.status.${shown.tag}"),
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (shown is FriendStatus.Solved) Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(ChipIcon))
                Text(shown.label, style = MaterialTheme.typography.labelLarge.tabular(), maxLines = 1)
            }
        }
    }
}

private class ChipLook(val container: Color, val content: Color)

@Composable
private fun chipLook(status: FriendStatus): ChipLook {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        FriendStatus.New -> ChipLook(scheme.secondaryContainer, scheme.onSecondaryContainer)
        is FriendStatus.InProgress -> ChipLook(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        is FriendStatus.Solved -> ChipLook(scheme.primaryContainer, scheme.onPrimaryContainer)
        FriendStatus.TakenDown -> LocalMutedStatus.current.let { ChipLook(it.container, it.content) }
        FriendStatus.NeedsUpdate -> ChipLook(scheme.errorContainer, scheme.onErrorContainer)
    }
}

private val FriendStatus.label: String
    get() = when (this) {
        FriendStatus.New -> "New"
        is FriendStatus.InProgress -> "In progress · ${formatDuration(elapsedMs)}"
        is FriendStatus.Solved -> "Solved ${formatDuration(timeMs)}"
        FriendStatus.TakenDown -> "Taken down · still plays"
        FriendStatus.NeedsUpdate -> "Needs update"
    }

private val FriendStatus.tag: String
    get() = when (this) {
        FriendStatus.New -> "new"
        is FriendStatus.InProgress -> "in_progress"
        is FriendStatus.Solved -> "solved"
        FriendStatus.TakenDown -> "taken_down"
        FriendStatus.NeedsUpdate -> "needs_update"
    }

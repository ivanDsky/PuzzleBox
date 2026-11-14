package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MorphCell
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.R
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus

private val ChipHeight = 28.dp
private val TouchTarget = 48.dp
private val LogoSize = 18.dp
private val CheckSize = 16.dp
private val RingStroke = 2.dp

/** What the chip's one changing cell draws: a check, words, or the ring. Faces that look alike are one face. */
private data class ChipFace(val label: String?, val busy: Boolean)

/**
 * The day card's one LinkedIn mark: the LinkedIn logo and the sync state, on a solid surface of its own so it reads
 * on any accent fill. The visible chip is [ChipHeight] tall inside a [TouchTarget] box, which is the tap target when
 * the state offers an action (Resume, Sign in again, Retry); synced and syncing are not tappable. [onReconnect] is
 * null when there is no login to open. Draws nothing while LinkedIn is not connected.
 *
 * A [status] that changes morphs in place and the chip's width follows over [MotionTokens.DayMorphResize]; one that
 * looks the same (imported, not played, idle) moves nothing. While [reading] the settled [status] stays, and its mark
 * is a progress ring of the same size, so the chip neither flips to a label nor changes width. [status] is Loading
 * only while nothing is settled yet: logo and ring.
 */
@Composable
internal fun LinkedInSyncChip(
    status: LinkedInDayStatus,
    reading: Boolean = false,
    onResume: () -> Unit,
    onReconnect: (() -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chip = syncChipOf(status, onResume, onReconnect, onRetry) ?: return
    val busy = chip.busy || reading
    val tap = if (busy) null else chip.onClick
    val reducedMotion = rememberReducedMotion()
    Box(
        modifier = modifier
            .heightIn(min = TouchTarget)
            .then(if (tap == null) Modifier else Modifier.clickable(role = Role.Button, onClick = tap))
            .semantics(mergeDescendants = true) { contentDescription = if (busy) SYNCING else chip.description }
            .testTag("linkedin.sync"),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier = Modifier
                    .height(ChipHeight)
                    .animateContentSize(if (reducedMotion) snap() else tween(MotionTokens.DayMorphResize, easing = MotionTokens.EasingStandard))
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_linkedin_dot), contentDescription = null, modifier = Modifier.size(LogoSize))
                MorphCell(ChipFace(if (busy) null else chip.label, busy)) { face ->
                    when {
                        face.busy -> CircularProgressIndicator(
                            modifier = Modifier.size(CheckSize).testTag("linkedin.sync.ring"),
                            color = LocalContentColor.current,
                            strokeWidth = RingStroke,
                        )
                        face.label == null -> Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(CheckSize))
                        else -> Text(
                            face.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

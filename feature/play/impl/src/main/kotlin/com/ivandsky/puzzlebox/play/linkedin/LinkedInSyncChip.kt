package com.ivandsky.puzzlebox.play.linkedin

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
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.R
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus

private val ChipHeight = 28.dp
private val TouchTarget = 48.dp
private val LogoSize = 18.dp
private val CheckSize = 16.dp

/**
 * The day card's one LinkedIn mark: the LinkedIn logo and the sync state, on a solid surface of its own so it reads
 * on any accent fill. The visible chip is [ChipHeight] tall inside a [TouchTarget] box, which is the tap target when
 * the state offers an action (Resume, Sign in again, Retry); synced and syncing are not tappable. [onReconnect] is
 * null when there is no login to open. Draws nothing while LinkedIn is not connected.
 */
@Composable
internal fun LinkedInSyncChip(
    status: LinkedInDayStatus,
    onResume: () -> Unit,
    onReconnect: (() -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chip = syncChipOf(status, onResume, onReconnect, onRetry) ?: return
    val tap = chip.onClick
    Box(
        modifier = modifier
            .heightIn(min = TouchTarget)
            .then(if (tap == null) Modifier else Modifier.clickable(role = Role.Button, onClick = tap))
            .semantics(mergeDescendants = true) { contentDescription = chip.description }
            .testTag("linkedin.sync"),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier = Modifier
                    .height(ChipHeight)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_linkedin_dot), contentDescription = null, modifier = Modifier.size(LogoSize))
                if (chip.label == null) {
                    Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(CheckSize))
                } else {
                    Text(
                        chip.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

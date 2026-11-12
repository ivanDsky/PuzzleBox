package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.R
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.linkedin.SavedBoardState
import java.time.ZoneId

private val LogoSize = 14.dp

/**
 * The day's LinkedIn board line (A36): a saved board reads "as of" the time it was read, with a Partial or Final mark
 * (a fixed time, never a relative age); a day that was never saved says "Not saved" and how to keep the next one; a day
 * still inside the days LinkedIn serves draws nothing, because the sync chip's read is its whole state. The line needs
 * no LinkedIn link: a saved board is the player's own, whether or not they are connected. [zone] is the zone the time
 * is shown in, the device's.
 */
@Composable
internal fun SavedBoardLine(saved: SavedBoardState, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault()) {
    val line = remember(saved, zone) { saved.toDayLine(zone) } ?: return
    val text = when (line) {
        is DayLine.Saved -> "Saved board · as of ${line.asOf}"
        DayLine.NeverSaved -> DayLine.NEVER_SAVED
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = (line as? DayLine.Saved)?.description ?: text }
            .testTag(if (line is DayLine.Saved) "linkedin.saved_board" else "linkedin.never_saved"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_linkedin_dot), contentDescription = null, modifier = Modifier.size(LogoSize))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false).testTag("linkedin.saved_board.text"),
        )
        if (line is DayLine.Saved) {
            Text(
                line.mark,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    .testTag("linkedin.saved_board.mark"),
            )
        }
    }
}

package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

private val CardHeight = 56.dp

/**
 * Today's recap card (A2, A3): a summary row for the period in [slot], tappable as a whole to open the recap, with a
 * close button at its end that hides the card for that period. Looks like the other summary rows, one line taller for
 * its second line. Tags: `summary.recap` for the card, `summary.recap.dismiss` for the close button.
 */
@Composable
internal fun HomeRecapCard(slot: RecapSlot, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .heightIn(min = CardHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = slot.onOpen)
                .heightIn(min = CardHeight)
                .padding(start = Spacing.xl, top = Spacing.sm, bottom = Spacing.sm)
                .testTag("summary.recap"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Your ${slot.label} recap",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "See how the month went",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(PuzzleIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = slot.onDismiss, modifier = Modifier.padding(end = Spacing.xs).testTag("summary.recap.dismiss")) {
            Icon(
                PuzzleIcons.Close,
                contentDescription = "Dismiss ${slot.label} recap",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

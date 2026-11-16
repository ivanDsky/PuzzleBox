package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

private val DoorMinHeight = 48.dp

/**
 * The streak row (flame, the day count and the freeze count) that the phone card and the
 * tablet pane share. With [onOpen] it is one full-width button of at least 48dp, with a chevron
 * at its trailing edge as its cue, that opens the Ranks calendar (nav rethink N3); with null it is the plain
 * [content] row, as before. The caller's [content] sets the row's look; it is tagged
 * `home.streak_door` only as a button.
 */
@Composable
internal fun StreakDoor(
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    if (onOpen == null) {
        Row(modifier, horizontalArrangement, verticalAlignment, content)
        return
    }
    // The chevron closes the row, so [content] gets its own row that takes what the chevron leaves.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home.streak_door")
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = "Open the streak calendar in Ranks", role = Role.Button, onClick = onOpen)
            .heightIn(min = DoorMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement, verticalAlignment, content)
        Icon(
            imageVector = PuzzleIcons.Chevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

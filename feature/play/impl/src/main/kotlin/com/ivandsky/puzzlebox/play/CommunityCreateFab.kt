package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The create FAB's side: above the 48dp touch target. */
private val CreateFabSize = 56.dp

/**
 * How much of a Community list's end the create FAB covers: its size and its margin to the edge and above it. The
 * lists keep this much bottom padding and the snackbars sit above it, so the FAB hides no row and no Undo.
 */
internal val CreateFabClearance: Dp = CreateFabSize + Spacing.lg * 2

/**
 * The Community screen's create button (#2013): the create icon, read as "New puzzle, button". It hangs off the Community
 * screen, not a tab; the screen places it at the bottom end of the pane it floats over with [modifier]'s `align`.
 * [onClick] opens the New puzzle sheet.
 */
@Composable
internal fun CommunityCreateFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.padding(Spacing.lg).size(CreateFabSize).testTag("community.fab"),
    ) {
        Icon(PuzzleIcons.Create, contentDescription = "New puzzle")
    }
}

@PuzzleBoxPreviews
@Composable
internal fun CommunityCreateFabPreview() {
    PuzzleBoxTheme { CommunityCreateFab(onClick = {}) }
}

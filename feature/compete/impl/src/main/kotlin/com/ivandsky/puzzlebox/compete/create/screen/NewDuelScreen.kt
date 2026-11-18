package com.ivandsky.puzzlebox.compete.create.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.compete.create.NewDuelActions
import com.ivandsky.puzzlebox.compete.create.NewDuelContent
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

/**
 * New duel as a page: a bar with the way out, then the sheet's own body, which names itself. [onBack] is the bar's
 * arrow. [pane]: the tablet's pane draws it, so the bar shows Close in place of the Back arrow.
 */
@Composable
internal fun NewDuelScreen(
    state: NewDuelState,
    actions: NewDuelActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pane: Boolean = false,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("duel.new.screen")) {
        CompactTopBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag(if (pane) "duel.new.close" else "duel.new.back")) {
                    if (pane) Icon(PuzzleIcons.Close, contentDescription = "Close") else Icon(PuzzleIcons.Back, contentDescription = "Back")
                }
            },
        )
        NewDuelContent(state, actions)
    }
}

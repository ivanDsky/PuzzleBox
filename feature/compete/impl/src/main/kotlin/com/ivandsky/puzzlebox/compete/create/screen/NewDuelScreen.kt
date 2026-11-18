package com.ivandsky.puzzlebox.compete.create.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.create.DuelStep
import com.ivandsky.puzzlebox.compete.create.NewDuelActions
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.SignInPanel
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

/** The form and the Create bar stop growing here and sit centred, so a wide window does not stretch them. */
internal val NewDuelMaxWidth = 640.dp

/**
 * New duel as a page: "New duel" in the bar, the scrolling form, and the Create bar pinned under it. A signed-out
 * friend duel shows the sign-in panel in their place. [onBack] is the bar's arrow. [pane]: the tablet's pane draws it,
 * so the bar shows Close in place of the Back arrow.
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
            title = "New duel",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag(if (pane) "duel.new.close" else "duel.new.back")) {
                    if (pane) Icon(PuzzleIcons.Close, contentDescription = "Close") else Icon(PuzzleIcons.Back, contentDescription = "Back")
                }
            },
        )
        when (state.step) {
            DuelStep.Create -> {
                NewDuelForm(state, actions, Modifier.weight(1f))
                NewDuelCreateBar(state, actions)
            }
            DuelStep.SignIn -> Box(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.widthIn(max = NewDuelMaxWidth).fillMaxWidth()) { SignInPanel(state, actions) }
            }
        }
    }
}

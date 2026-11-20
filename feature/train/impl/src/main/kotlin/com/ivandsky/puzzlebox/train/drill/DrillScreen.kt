package com.ivandsky.puzzlebox.train.drill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.train.round.RoundCantLoad
import com.ivandsky.puzzlebox.train.round.RoundEmpty
import com.ivandsky.puzzlebox.train.round.RoundHeader
import com.ivandsky.puzzlebox.train.round.RoundNeedsConnection

/** What the sandbox's top bar says the screen is, after the technique's name. */
private const val PRACTICE_LABEL = "Practice puzzle"

/**
 * A technique's drill: while [state] is [DrillState.Playing] the game's own play screen, handed in as [play] and drawn
 * under its own top bar (Up, the technique's [name], "Practice puzzle"), with no clock; [onClose] is that Up. Otherwise
 * [name] as the title over a spinner while it loads, the empty message, the needs-a-connection or can't-load message, or
 * the solved page. [showBack] adds a back arrow to the title row (the phone, where the drill covers the list). The play
 * screen takes the whole pane on one pane, whatever the window: the drill's pane is never wide enough for a side panel.
 * [onSolved] is the play screen's report that the solve sweep has played, [onTryAgain] the needs-a-connection action,
 * which fetches a puzzle, and [onTryAnother] the can't-load action, which opens a puzzle already on the device.
 */
@Composable
internal fun DrillScreen(
    name: String,
    state: DrillState,
    showBack: Boolean,
    onClose: () -> Unit,
    play: DrillPlaySlot,
    onSolved: () -> Unit,
    onTryAgain: () -> Unit,
    onTryAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is DrillState.Playing -> play.content(
            state.ui,
            state.controller,
            name,
            PRACTICE_LABEL,
            PlayLayout.SinglePane,
            onClose,
            onSolved,
            modifier.fillMaxSize().testTag("train.drill"),
        )
        DrillState.Loading -> DrillPage(name, showBack, onClose, modifier) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        }
        DrillState.Empty -> DrillPage(name, showBack, onClose, modifier) { RoundEmpty(name, onClose) }
        DrillState.NeedsConnection -> DrillPage(name, showBack, onClose, modifier) { RoundNeedsConnection(name, onTryAgain) }
        is DrillState.CantLoad -> DrillPage(name, showBack, onClose, modifier) { RoundCantLoad(name, onTryAnother) }
        DrillState.Solved -> DrillPage(name, showBack, onClose, modifier) { DrillSolved(onClose) }
    }
}

/** The title row over [content], for every state that is not the play screen. */
@Composable
private fun DrillPage(name: String, showBack: Boolean, onClose: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().padding(bottom = Spacing.lg).testTag("train.drill")) {
        RoundHeader(name, null, showBack, onClose)
        Spacer(Modifier.size(Spacing.sm))
        content()
    }
}

/** The solved page: one line, and the way back to the technique list. */
@Composable
private fun DrillSolved(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().testTag("train.drill.solved"),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Solved",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        PrimaryButton("Back to techniques", onClick = onClose)
    }
}

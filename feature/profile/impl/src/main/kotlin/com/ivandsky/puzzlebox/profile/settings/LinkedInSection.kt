package com.ivandsky.puzzlebox.profile.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Settings LinkedIn block. Stateless: it draws [state] and reports taps. Connect does not warn here,
 * the login route does. Disconnect acts at once because connecting again undoes it and imports are kept.
 * A paused link that needs a reconnect offers Reconnect (same callback as Connect) instead of Resume,
 * since resuming a rejected session would only pause again.
 */
@Composable
internal fun LinkedInSection(
    state: LinkedInSectionState,
    onConnect: () -> Unit,
    onResume: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("settings.linkedin"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = "LinkedIn", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        when (state) {
            LinkedInSectionState.NotConnected -> {
                Line("Import your own LinkedIn game results and see your connections' times on this phone.")
                ActionButton("Connect", "settings.linkedin.connect", onConnect)
            }
            LinkedInSectionState.Connected -> {
                Line("Connected to LinkedIn")
                ActionButton("Disconnect", "settings.linkedin.disconnect", onDisconnect)
            }
            is LinkedInSectionState.Paused -> {
                Line(state.reason)
                if (state.needsReconnect) {
                    ActionButton("Reconnect", "settings.linkedin.connect", onConnect)
                } else {
                    ActionButton("Resume", "settings.linkedin.resume", onResume)
                }
                ActionButton("Disconnect", "settings.linkedin.disconnect", onDisconnect)
            }
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ActionButton(text: String, tag: String, onClick: () -> Unit) {
    SecondaryButton(text = text, onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag))
}

@PuzzleBoxPreviews
@Composable
internal fun LinkedInSectionNotConnectedPreview() {
    PuzzleBoxTheme {
        LinkedInSection(LinkedInSectionState.NotConnected, onConnect = {}, onResume = {}, onDisconnect = {})
    }
}

@PuzzleBoxPreviews
@Composable
internal fun LinkedInSectionConnectedPreview() {
    PuzzleBoxTheme {
        LinkedInSection(LinkedInSectionState.Connected, onConnect = {}, onResume = {}, onDisconnect = {})
    }
}

@PuzzleBoxPreviews
@Composable
internal fun LinkedInSectionPausedPreview() {
    PuzzleBoxTheme {
        LinkedInSection(
            LinkedInSectionState.Paused(reason = "LinkedIn is busy. Try again in a while.", needsReconnect = false),
            onConnect = {},
            onResume = {},
            onDisconnect = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun LinkedInSectionReconnectPreview() {
    PuzzleBoxTheme {
        LinkedInSection(
            LinkedInSectionState.Paused(reason = "LinkedIn signed this phone out.", needsReconnect = true),
            onConnect = {},
            onResume = {},
            onDisconnect = {},
        )
    }
}

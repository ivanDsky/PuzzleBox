package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The first ranked solve's question: share replays with the players you allow, or keep them private. The copy promises
 * only future solves and names Settings as the way back. [onDismiss] (swipe away, scrim) is a "keep private".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplayOptInSheet(onShare: () -> Unit, onKeepPrivate: () -> Unit, modifier: Modifier = Modifier) {
    ModalBottomSheet(onDismissRequest = onKeepPrivate, modifier = modifier.testTag("ranks.optin.sheet")) {
        ReplayOptInContent(onShare, onKeepPrivate)
    }
}

@Composable
private fun ReplayOptInContent(onShare: () -> Unit, onKeepPrivate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Share your replays?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("ranks.optin.title"))
        Text(
            "You're on the board. Let other players watch how you solved, from your next solve on. Change it any time in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onShare, Modifier.fillMaxWidth().testTag("ranks.optin.share")) { Text("Share my replays") }
        OutlinedButton(onKeepPrivate, Modifier.fillMaxWidth().testTag("ranks.optin.private")) { Text("Keep private") }
    }
}

@PuzzleBoxPreviews
@Composable
private fun ReplayOptInSheetPreview() {
    PuzzleBoxTheme { ReplayOptInContent(onShare = {}, onKeepPrivate = {}) }
}

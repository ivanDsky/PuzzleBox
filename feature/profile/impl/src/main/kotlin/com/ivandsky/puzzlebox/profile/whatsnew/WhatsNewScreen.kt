package com.ivandsky.puzzlebox.profile.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** What's new, a pushed page like Settings: a compact top bar with Back over one card per release, newest first. */
@Composable
internal fun WhatsNewScreen(state: WhatsNewUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompactTopBar(
            title = "What's new",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("whatsNew.back")) {
                    Icon(
                        imageVector = PuzzleIcons.Back,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("whatsNew.list"),
            contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (state.loadFailed) {
                item {
                    Text(
                        text = "What's new could not load.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("whatsNew.failed"),
                    )
                }
            }
            items(state.releases, key = { it.version }) { ReleaseCard(it) }
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun WhatsNewScreenPreview() {
    PuzzleBoxTheme {
        WhatsNewScreen(
            state = WhatsNewUiState(
                listOf(
                    WhatsNewCard("0.36.2", "18 Nov 2026", listOf("Ranks opens on Global until you have a friend.")),
                    WhatsNewCard("0.36.1", "18 Nov 2026", listOf("A small clean-up with nothing new to see.")),
                ),
            ),
            onBack = {},
        )
    }
}

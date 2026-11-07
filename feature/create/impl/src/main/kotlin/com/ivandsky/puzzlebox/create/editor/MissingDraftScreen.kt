package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Studio for a draft this build can't open (gone, or a game or format it doesn't know): a
 * compact bar whose Back ([onBack]) returns to the Create root, over one centred line.
 */
@Composable
internal fun MissingDraftScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CompactTopBar(
            title = "Draft",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("create.editor.missing.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back to drafts")
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
            Text(
                text = "This draft can't be opened.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("create.editor.missing"),
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun MissingDraftScreenPreview() {
    PuzzleBoxTheme { MissingDraftScreen(onBack = {}) }
}

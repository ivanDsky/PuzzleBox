package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The replay page of a solve that has none (a missing, unreadable or unsupported replay): the same flat top bar and
 * padding as [ReplayPage], with [ReplayCopy.NO_REPLAY] where the player would be. [context] is the page's
 * "Game · #N · day" line.
 */
@Composable
fun ReplayEmptyPage(context: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        ReplayTopBar(ReplayCopy.TITLE, context, onBack)
        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                ReplayCopy.NO_REPLAY,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ReplayTags.NONE),
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplayEmptyPagePreview() {
    PuzzleBoxTheme { ReplayEmptyPage("Queens · #7 · Tue, Nov 4", onBack = {}) }
}

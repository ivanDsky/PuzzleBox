package com.ivandsky.puzzlebox.profile.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** What's new, a pushed page: a compact top bar with Back over an empty scroll area the release list fills. */
@Composable
internal fun WhatsNewScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
                .testTag("whatsNew.list"),
        ) {}
    }
}

@PuzzleBoxPreviews
@Composable
internal fun WhatsNewScreenPreview() {
    PuzzleBoxTheme { WhatsNewScreen(onBack = {}) }
}

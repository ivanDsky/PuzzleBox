package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

// A 30% side pane on a 1280dp-wide tablet.
private val PreviewPaneWidth = 384.dp

@Composable
private fun CardPreview(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Column(Modifier.width(PreviewPaneWidth).background(MaterialTheme.colorScheme.surface).padding(Spacing.lg)) {
            content()
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun DuelProgressCardPreview() = CardPreview { DuelProgressCard(played = 1, count = 3) }

@PuzzleBoxPreviews
@Composable
internal fun HintsCardPreview() = CardPreview { HintsCard(used = 2) }

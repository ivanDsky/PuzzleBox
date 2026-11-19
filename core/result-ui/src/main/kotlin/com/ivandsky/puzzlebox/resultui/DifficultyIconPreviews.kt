package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Difficulty

// Preview of the difficulty marks — internal, light + dark inside PuzzleBoxTheme: every level
// as a bare mark at 14, 16 and 18dp, then as the label users see.

@PuzzleBoxPreviews
@Composable
internal fun DifficultyIconPreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Difficulty.Level.entries.forEach { level ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    listOf(14.dp, 16.dp, 18.dp).forEach { size ->
                        DifficultyIcon(level, Modifier.size(size), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DifficultyLabel(level, MaterialTheme.typography.labelLarge, MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

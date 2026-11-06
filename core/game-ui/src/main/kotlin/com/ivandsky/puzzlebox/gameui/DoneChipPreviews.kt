package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.TodayStatus

// Previews of DoneChip: a short and a typical solve, the hour-long "1:02:03" (the width
// the time column is sized for), a long name, and the offline paint.

@PuzzleBoxPreviews
@Composable
internal fun DoneChipPreview() {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(
                previewCardModel(TodayStatus.Solved(42_000L), AccentSlot.Teal).copy(name = "Zip"),
                previewCardModel(TodayStatus.Solved(754_000L), AccentSlot.Pink).copy(name = "Queens"),
                previewCardModel(TodayStatus.Solved(3_723_000L), AccentSlot.Violet),
                previewCardModel(TodayStatus.Solved(3_723_000L), AccentSlot.Teal).copy(name = "Mini Sudoku Classic Edition"),
                previewCardModel(TodayStatus.Offline, AccentSlot.Pink),
            ).forEach { DoneChip(it, onClick = {}) }
        }
    }
}

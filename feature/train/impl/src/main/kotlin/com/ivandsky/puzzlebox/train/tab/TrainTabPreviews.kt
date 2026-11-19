package com.ivandsky.puzzlebox.train.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme

// The Train tab body, light and dark. It is empty until the technique list lands.

@PuzzleBoxPreviews
@Composable
private fun TrainTabPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { TrainTab() }
}

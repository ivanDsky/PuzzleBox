package com.ivandsky.puzzlebox.gameui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme

// Preview of singlePaneDeviceClass — the class the preview window reads as, light + dark.

@PuzzleBoxPreviews
@Composable
internal fun SinglePaneDeviceClassPreview() {
    PuzzleBoxTheme { Text("single pane: ${singlePaneDeviceClass()}") }
}

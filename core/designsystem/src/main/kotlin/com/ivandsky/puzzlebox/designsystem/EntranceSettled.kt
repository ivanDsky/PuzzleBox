package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode

/** Whether entrance motion here starts in its end state: in a preview, whose inspector never runs effects. */
@Composable
@ReadOnlyComposable
fun entranceSettled(): Boolean = LocalInspectionMode.current

// Preview: a beat block in its end state from the first frame (static, A8).
@PuzzleBoxPreviews
@Composable
internal fun EntranceSettledPreview() {
    PuzzleBoxTheme {
        Text(
            text = if (entranceSettled()) "Entrance settled" else "Entrance plays",
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.md)
                .resultBeat(ResultBeats.Actions),
        )
    }
}

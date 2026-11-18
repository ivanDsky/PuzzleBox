package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** A settled pane: the page is the only one in the tree, as at rest. */
@PuzzleBoxPreviews
@Composable
internal fun HoldCrossfadePreview() = PuzzleBoxTheme {
    HoldCrossfade("Puzzle K7Q2MX") { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(it) } }
}

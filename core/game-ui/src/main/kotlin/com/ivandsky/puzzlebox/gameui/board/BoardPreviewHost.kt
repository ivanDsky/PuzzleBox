package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme

/**
 * The board kit's previews' one host: the theme on a surface of the screen's background colour, so a dark preview draws on a dark
 * page rather than on the preview tool's light one. [modifier] sizes the surface, for a preview of a whole screen.
 */
@Composable
internal fun BoardPreviewHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Surface(modifier, color = MaterialTheme.colorScheme.background, content = content)
    }
}

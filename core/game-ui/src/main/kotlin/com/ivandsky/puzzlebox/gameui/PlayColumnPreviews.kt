package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme

// Previews of PlayColumn on a tablet-landscape canvas, the board a plain tile: a caption
// under the board, and the degrade banner under the header.

private val PreviewBoardSide = 480.dp
private val PreviewScreenWidth = 1280.dp
private val PreviewScreenHeight = 800.dp

private object PreviewClock : PlayClock {
    override val clockWindow: Any? = null
    override fun tick() = Unit
    override fun onBackground() = Unit
    override fun onForeground() = Unit
    override fun displayedElapsedMs(): Long = 83_000
}

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(width = PreviewScreenWidth, height = PreviewScreenHeight),
            content = content,
        )
    }
}

@Composable
private fun PreviewBoard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.size(PreviewBoardSide),
    ) {}
}

@PuzzleBoxPreviews
@Composable
private fun PlayColumnCaptionPreview() {
    PreviewFrame {
        PlayColumn(
            title = "Mini Sudoku",
            subtitle = "Daily · #450",
            clock = PreviewClock,
            caption = "Two 2s in row 2",
            banner = null,
            onHowToPlay = {},
        ) { PreviewBoard() }
    }
}

@PuzzleBoxPreviews
@Composable
private fun PlayColumnBannerPreview() {
    PreviewFrame {
        PlayColumn(
            title = "Mini Sudoku",
            subtitle = "Daily",
            clock = PreviewClock,
            caption = null,
            banner = "Offline — playing a saved puzzle.",
            onHowToPlay = {},
        ) { PreviewBoard() }
    }
}

package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/** A flat 6dp progress bar: the fill touches the track, with no gap and no end dot. */
@Composable
fun FlatProgressBar(progress: () -> Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier.height(6.dp).clip(CircleShape),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@PuzzleBoxPreviews
@Composable
private fun FlatProgressBarPreview() {
    PuzzleBoxTheme {
        FlatProgressBar(progress = { 0.6f }, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(160.dp).padding(Spacing.lg))
    }
}

package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The pane's equal inset above and below its board; [ToolbarBoardPane]'s cap maths reads the same value. */
internal val PlayBoardInset = Spacing.lg

/**
 * The board pane every game draws its board in: the full pane, [content] centred in it,
 * with an equal [PlayBoardInset] above and below. A board shorter than its pane floats
 * at its vertical middle — between the top chrome and the bottom controls — instead of
 * hugging the top; callers add only their own sideways gutter through [modifier].
 */
@Composable
fun PlayBoardPane(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxSize().padding(vertical = PlayBoardInset),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// A board shorter than its pane: equal space above and below.
@PuzzleBoxPreviews
@Composable
internal fun PlayBoardPaneShortBoardPreview() {
    PuzzleBoxTheme {
        Surface(Modifier.size(width = 360.dp, height = 480.dp)) {
            PlayBoardPane(Modifier.padding(horizontal = Spacing.lg)) {
                Box(Modifier.size(240.dp).background(MaterialTheme.colorScheme.primaryContainer))
            }
        }
    }
}

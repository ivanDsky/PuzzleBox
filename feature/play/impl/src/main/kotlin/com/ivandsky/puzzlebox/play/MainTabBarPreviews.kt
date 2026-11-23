package com.ivandsky.puzzlebox.play

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout

// Games on Today's large top bar: phone at its narrowest and the tablet, light and dark.

@Composable
private fun Games(layout: PlayLayout) {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            LibraryScreen(
                state = previewState(),
                page = null,
                month = null,
                layout = layout,
                hasLesson = { true },
                onSelect = {},
                onPlay = { _, _ -> },
                onRetry = {},
                onOpenLesson = {},
                calendar = CalendarActions(),
                onOpenRanks = { _, _ -> },
                onEnterCode = {},
                onMakeOne = {},
            )
        }
    }
}

@Preview(name = "Phone Light", widthDp = 360, heightDp = 640, showBackground = true)
@Preview(name = "Phone Dark", widthDp = 360, heightDp = 640, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun GamesLargeBarPhonePreview() = Games(PlayLayout.SinglePane)

@Preview(name = "Tablet Light", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(name = "Tablet Dark", widthDp = 1280, heightDp = 800, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun GamesLargeBarTabletPreview() = Games(PlayLayout.TwoPane)

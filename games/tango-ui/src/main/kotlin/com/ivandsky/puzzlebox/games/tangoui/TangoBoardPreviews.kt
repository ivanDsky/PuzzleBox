package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel

// The Tango board under the readable board tokens (nav-rethink B3), in light and dark: the render to hold against
// the approved token sheets. TangoReadableBoardGoldenTest pins the same composable.

/** A mid-play board in the readable look: givens, the player's own entries, all four signs and two broken lines. */
@Composable
internal fun TangoReadableBoardSample(dark: Boolean) {
    PuzzleBoxTheme(darkTheme = dark, readableBoard = true) {
        TangoBoardRenderer().Content(
            previewBoard(),
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(Spacing.md),
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun TangoReadableBoardPreview() {
    PuzzleBoxTheme { TangoReadableBoardSample(dark = LocalDarkTheme.current) }
}

/** Row 1 holds three suns in a row and row 5 three moons, so both show the broken-line state. */
private fun previewBoard() = TangoRendererModel(
    n = 6,
    cells = (0 until 36).map { idx ->
        TangoRendererModel.CellRender(
            value = when (idx) {
                in setOf(0, 1, 6, 7, 8, 16, 29) -> TangoCell.SUN
                in setOf(2, 14, 22, 32, 33, 34) -> TangoCell.MOON
                else -> null
            },
            isGiven = idx in setOf(0, 1, 6, 7, 14, 16, 22, 29, 32, 33),
        )
    },
    markers = listOf(
        TangoRendererModel.MarkerRender(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
        TangoRendererModel.MarkerRender(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        TangoRendererModel.MarkerRender(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
        TangoRendererModel.MarkerRender(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
    ),
    rejection = null,
    status = TangoRendererModel.Status.PLAYING,
)

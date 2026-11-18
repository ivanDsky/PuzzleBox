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

// The Tango board in light and dark: the render to hold against the approved token sheets.
// TangoReadableBoardGoldenTest pins the same composable.

/** A mid-play board givens, the player's own entries, all four signs and two broken lines. */
@Composable
internal fun TangoReadableBoardSample(dark: Boolean) {
    PuzzleBoxTheme(darkTheme = dark) {
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

/** Tutorial lesson 1 as the player first sees it: three open squares, each ghosting its answer (#1186). */
@Composable
internal fun TangoLessonOneSample(dark: Boolean) {
    PuzzleBoxTheme(darkTheme = dark) {
        TangoBoardRenderer(guideGhosts = true).Content(
            lessonOneBoard(),
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(Spacing.md),
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun TangoLessonOnePreview() {
    PuzzleBoxTheme { TangoLessonOneSample(dark = LocalDarkTheme.current) }
}

/** `SSMM / .M.S / .MSM / MSMS`, answers M, S, S in the open squares (TangoTutorial lesson 1). */
private fun lessonOneBoard() = TangoRendererModel(
    n = 4,
    cells = "SSMM.M.S.MSMMSMS".map { ch ->
        TangoRendererModel.CellRender(
            value = when (ch) {
                'S' -> TangoCell.SUN
                'M' -> TangoCell.MOON
                else -> null
            },
            isGiven = ch != '.',
        )
    },
    markers = emptyList(),
    rejection = null,
    status = TangoRendererModel.Status.PLAYING,
    ghosts = mapOf(4 to TangoCell.MOON, 6 to TangoCell.SUN, 8 to TangoCell.SUN),
)

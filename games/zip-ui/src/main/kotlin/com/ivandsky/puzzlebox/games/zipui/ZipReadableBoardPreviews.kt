package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection

// The readable Zip board (nav-rethink B3), at rest: what the board tokens the user approved
// look like on a real Zip board. It shows no next-number mark: only the live play board does.

/**
 * A 6×6 board part-way through: the trace has passed "1" and "2" and stopped one cell short
 * of "3", the next number to reach. This static board carries no mark on it. A wall runs on
 * two of its sides. One wall runs into the top edge and one into the right edge next to the
 * corner, where a round wall cap would poke out of an unclipped board.
 */
internal fun zipReadablePreviewModel() = zipPreviewModel(
    n = 6,
    waypoints = listOf(0, 14, 21, 35),
    path = listOf(0, 6, 12, 13, 14, 15),
    walls = setOf(
        Wall(Cell(2), WallDirection.RIGHT),
        Wall(Cell(20), WallDirection.RIGHT),
        Wall(Cell(21), WallDirection.DOWN),
        Wall(Cell(24), WallDirection.DOWN),
        Wall(Cell(29), WallDirection.DOWN),
    ),
)

/** The readable board on the theme's surface, light or [dark]; the golden renders this too. */
@Composable
internal fun ZipReadableBoardSample(dark: Boolean) {
    PuzzleBoxTheme(darkTheme = dark) {
        Surface {
            ZipBoard(zipReadablePreviewModel(), Modifier.padding(24.dp))
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ZipReadableBoardPreview() {
    PuzzleBoxTheme {
        ZipReadableBoardSample(dark = LocalDarkTheme.current)
    }
}

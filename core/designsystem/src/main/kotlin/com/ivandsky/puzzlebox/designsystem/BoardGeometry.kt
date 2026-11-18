package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What a board draws that its colours cannot carry (nav-rethink B3): the wall weight. Walls have rounded ends, the next
 * number to reach wears a ring, and the board edge is a wall (a path cannot cross it) rather than a grid line, on every board.
 * A board reads [LocalBoardGeometry]; [PuzzleBoxTheme] provides [Readable].
 */
data class BoardGeometry(val wallWeight: Dp) {
    companion object {
        /** About 3× the 1.5 dp grid line. */
        val Readable = BoardGeometry(wallWeight = 4.5.dp)
    }
}

/** The board geometry [PuzzleBoxTheme] provides; [BoardGeometry.Readable] by default, so a preview or a test outside the theme draws the same board. */
val LocalBoardGeometry = staticCompositionLocalOf { BoardGeometry.Readable }

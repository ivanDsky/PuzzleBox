package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What a board draws that its colours cannot carry (nav-rethink B3): wall weight, whether wall ends are rounded, and
 * whether the next number to reach wears a ring, and whether the board edge is drawn as a wall (a path cannot cross it) rather than a grid line. [PuzzleBoxTheme] picks [Legacy] or [Readable] from its one
 * `readableBoard` Boolean, so a board reads [LocalBoardGeometry] and never a release flag (spec A2).
 */
data class BoardGeometry(
    val wallWeight: Dp,
    val roundedWallCaps: Boolean,
    val nextNumberRing: Boolean,
    val wallOutline: Boolean,
) {
    companion object {
        /** Today's board. */
        val Legacy = BoardGeometry(wallWeight = BoardTokens.boardWallWeight, roundedWallCaps = false, nextNumberRing = false, wallOutline = false)

        /** About 3× the 1.5 dp grid line, rounded ends, a ring on the next number to reach, the board edge drawn as a wall. */
        val Readable = BoardGeometry(wallWeight = 4.5.dp, roundedWallCaps = true, nextNumberRing = true, wallOutline = true)
    }
}

/** The board geometry [PuzzleBoxTheme] provides; [BoardGeometry.Legacy] by default so previews and tests keep today's look. */
val LocalBoardGeometry = staticCompositionLocalOf { BoardGeometry.Legacy }

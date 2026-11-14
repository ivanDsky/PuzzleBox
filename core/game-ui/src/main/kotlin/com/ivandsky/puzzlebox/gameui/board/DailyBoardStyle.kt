package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * How a board looks and moves; [defaults] is the plain board, and a caller names what it changes.
 *
 * [motion] is how the rows move. [backdrop] is what the board sits on: rows that overlap on the way are drawn opaque
 * on it, and so is the docked row; it may be see-through. With [filledRows] the rows of a board at rest are filled
 * with it too, for a board that sits on something its rows must stand off from; without, they are drawn with no fill
 * of their own. With [medals] the top three places are medal rows (see [MedalRow]). With [dock] the viewer's own row
 * docks to the edge of what scrolls the board while it is out of view (see [DockedSelfRow]). With [morphText] a new
 * title or caption frosts over the old one instead of replacing it. With [inlineMarks] a row's mark and detail are
 * chips beside its name, so every row is one line; without, they are small lines under the name.
 */
@Immutable
data class DailyBoardStyle(
    val motion: Motion,
    val backdrop: Color,
    val filledRows: Boolean,
    val medals: Boolean,
    val dock: Boolean,
    val morphText: Boolean,
    val inlineMarks: Boolean,
) {
    /** How a board's rows move. One choice, so a board cannot be between two boards and at rest at once. */
    @Immutable
    sealed interface Motion {
        /** Drawn plainly: every change lands at once. */
        data object Static : Motion

        /** Rows glide to a new place, arrive and leave with motion, and the podium's places are medals. */
        data object Animated : Motion

        /**
         * [Animated], and while it lasts the board is [morph]'s two boards drawn as one, as far along as it says.
         * Reduced motion ignores the morph.
         */
        data class Morphing(val morph: BoardMorph) : Motion
    }

    companion object {
        /** The plain board, at rest on the theme's background, with whatever the caller changes. */
        @Composable
        @ReadOnlyComposable
        fun defaults(
            motion: Motion = Motion.Static,
            backdrop: Color = MaterialTheme.colorScheme.background,
            filledRows: Boolean = false,
            medals: Boolean = false,
            dock: Boolean = false,
            morphText: Boolean = false,
            inlineMarks: Boolean = false,
        ): DailyBoardStyle = DailyBoardStyle(motion, backdrop, filledRows, medals, dock, morphText, inlineMarks)
    }
}

/**
 * What the board's rows sit on, when they have to be drawn opaque: rows that glide past each other would show through
 * each other if they were not. Null on a board whose rows never overlap, which draws them with no fill of their own.
 */
internal val LocalBoardBackdrop = staticCompositionLocalOf<Color?> { null }

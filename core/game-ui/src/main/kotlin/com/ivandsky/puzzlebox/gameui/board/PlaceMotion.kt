package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * What a row's place and time cells need to know about the motion around them: whether they roll at all, how long to
 * wait before they start (the row's stagger, so they move with its glide), and whether the row has just arrived on the
 * board (an arriving row on the podium flips its medal in; the first list's rows do not).
 */
internal interface PlaceMotion {
    val animated: Boolean
    val delayMs: Int
    val arrived: Boolean
}

/** Reduced motion: the final place and its medal are drawn at once, nothing rolls or flips. */
internal object StillPlaces : PlaceMotion {
    override val animated = false
    override val delayMs = 0
    override val arrived = false
}

/** The motion of the row being composed; null on a board without the LinkedIn account's motion, which has no medals. */
internal val LocalPlaceMotion = staticCompositionLocalOf<PlaceMotion?> { null }

/**
 * What the board's rows sit on, when they have to be drawn opaque: rows that glide past each other would show through
 * each other if they were not. Null on a board whose rows never overlap, which draws them with no fill of their own.
 */
internal val LocalBoardBackdrop = staticCompositionLocalOf<Color?> { null }

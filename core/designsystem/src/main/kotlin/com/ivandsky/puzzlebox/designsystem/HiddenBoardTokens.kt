package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The look of a board that is hidden until its puzzle is played: the real board, blurred past reading,
 * under a veil and a pill. Values are the prototype's (05, variant A).
 */
object HiddenBoardTokens {
    /** The blur's spread: the standard deviation of its Gaussian, as CSS `blur(18px)` reads it. */
    val Blur: Dp = 18.dp

    /** Colour kept after the blur: 1 is untouched, 0 is grey. */
    const val Saturation: Float = 0.6f

    /** Contrast kept after the blur, around mid grey. */
    const val Contrast: Float = 0.85f

    /** The blurred board is drawn this much larger than its slot, so its soft edge falls outside the slot's clip. */
    const val Scale: Float = 1.18f

    /** Opacity of the surface colour laid over the blurred board. */
    const val VeilAlpha: Float = 0.28f

    /** The slot's greatest height; its width follows the board's shape. */
    val MaxSide: Dp = 400.dp

    /** "Hidden until you play": the pill's least height, its icon and the gap between icon and words. */
    val PillMinHeight: Dp = 40.dp
    val PillIcon: Dp = 20.dp
    val PillGap: Dp = 8.dp
}

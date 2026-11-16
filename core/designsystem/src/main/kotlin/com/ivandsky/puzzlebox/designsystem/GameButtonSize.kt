package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A [GameButton]'s height floor and whether its label shows. */
enum class GameButtonSize(
    private val minHeight: Dp,
    internal val showsLabel: Boolean,
    private val flatMinHeight: Dp = minHeight,
) {
    /** Keypad and side-pane controls: the 48dp touch floor (spec A10). */
    Regular(ButtonMinHeight, showsLabel = true),

    /** The play action bar: a 64dp face on its lip, or 64dp in all once flat drops the lip. */
    Tall(64.dp + GameButtonLipDepth, showsLabel = true, flatMinHeight = 64.dp),

    /** A tile too narrow for a label: the icon alone, the label read out as its description. */
    Compact(ButtonMinHeight, showsLabel = false),
    ;

    /** The height floor: lip included, or the face alone when [flat] surfaces have no lip. */
    internal fun minHeight(flat: Boolean): Dp = if (flat) flatMinHeight else minHeight
}

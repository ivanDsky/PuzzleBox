package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A [GameButton]'s height floor, lip included, and whether its label shows. */
enum class GameButtonSize(internal val minHeight: Dp, internal val showsLabel: Boolean) {
    /** Keypad and side-pane controls: the 48dp touch floor (spec A10). */
    Regular(ButtonMinHeight, showsLabel = true),

    /** The play action bar: a 64dp face on its lip. */
    Tall(64.dp + GameButtonLipDepth, showsLabel = true),

    /** A tile too narrow for a label: the icon alone, the label read out as its description. */
    Compact(ButtonMinHeight, showsLabel = false),
}

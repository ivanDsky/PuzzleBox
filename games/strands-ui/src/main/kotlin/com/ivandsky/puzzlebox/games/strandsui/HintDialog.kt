package com.ivandsky.puzzlebox.games.strandsui

/** Which hint modal shows (A7): a hint already on the board, or none earned yet. */
sealed interface HintDialog {
    data object OnBoard : HintDialog

    data object NoneAvailable : HintDialog
}

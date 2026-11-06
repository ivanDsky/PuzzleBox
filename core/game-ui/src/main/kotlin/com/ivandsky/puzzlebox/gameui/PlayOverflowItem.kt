package com.ivandsky.puzzlebox.gameui

/**
 * One row of the play top bar's overflow menu (A14): a game setting that should not sit
 * on the main screen, such as Queens' Auto-cross. [checked] null is a plain action; true
 * or false is a toggle and shows its state. [onClick] runs the game's own change.
 */
data class PlayOverflowItem(val label: String, val checked: Boolean?, val onClick: () -> Unit)

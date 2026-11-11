package com.ivandsky.puzzlebox.profile.settings

/** The Games block's switches; every one ships on, matching the stored defaults. */
internal data class GamesSectionState(
    val queensAutoCross: Boolean = true,
    val sudokuPeerHighlight: Boolean = true,
    val sudokuSameDigitHighlight: Boolean = true,
)

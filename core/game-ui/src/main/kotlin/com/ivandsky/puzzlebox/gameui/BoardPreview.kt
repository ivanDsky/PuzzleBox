package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.Puzzle

/** One game's unsolved day as [GamePlayUi.hubLine] reads it: the save as the player left it, or today's untouched puzzle. */
sealed interface BoardPreview {
    /** An unfinished save: [bytes] are the stored SessionBlob, as [GamePlayController.adoptRestored] takes them. */
    class InProgress(val bytes: ByteArray) : BoardPreview

    /** Today's puzzle, not started. */
    data class Fresh(val puzzle: Puzzle) : BoardPreview
}

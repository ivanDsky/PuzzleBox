package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.Puzzle

/**
 * What a hub card's board thumbnail shows for one game's day: the save as the player left
 * it, today's untouched puzzle, or today's puzzle drawn solved (the save is gone once a
 * day is solved, so the game draws its own solution). [GamePlayUi.previewModel] reduces
 * it to the game's drawable model.
 */
sealed interface BoardPreview {
    /** An unfinished save: [bytes] are the stored SessionBlob, as [GamePlayController.adoptRestored] takes them. */
    class InProgress(val bytes: ByteArray) : BoardPreview

    /** Today's puzzle, not started. */
    data class Fresh(val puzzle: Puzzle) : BoardPreview

    /** Today's puzzle, solved: drawn as its solution. */
    data class Solved(val puzzle: Puzzle) : BoardPreview
}

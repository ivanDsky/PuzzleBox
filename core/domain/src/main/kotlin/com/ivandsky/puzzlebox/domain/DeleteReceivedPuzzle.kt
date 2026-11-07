package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * Removes a friend's puzzle from this device (spec A19): its received row and its saved play.
 * Its completion rows are not touched, so History keeps the record; opening the code again
 * fetches the puzzle anew.
 */
class DeleteReceivedPuzzle(private val received: ReceivedPuzzleRepository, private val saved: SavedPlayRepository) {
    /** Deletes the received_puzzles row and its PlaySlot.Custom saved play; the completion record stays (A19). */
    suspend operator fun invoke(code: ShareCode) {
        val puzzle = received.get(code) ?: return
        // The row goes first, so From friends drops the card with its status as it stood.
        received.delete(code)
        saved.delete(PlaySlot.Custom(puzzle.gameId, code.value))
    }
}

package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow

/**
 * The puzzles a Trainer round is cut from. Only what is already on the device: a source never fetches, so
 * a round opens offline and costs no network.
 */
fun interface TrainerPuzzleSource {

    /** [game]'s puzzles, newest first; cold, so each collection starts over from the newest. */
    fun puzzles(game: GameId): Flow<Puzzle>
}

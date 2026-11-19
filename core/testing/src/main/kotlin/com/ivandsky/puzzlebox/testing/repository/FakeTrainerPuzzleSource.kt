package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/** A [TrainerPuzzleSource] serving the puzzles a test scripts per game, in the order given (newest first); a game with none serves none. */
class FakeTrainerPuzzleSource(private val byGame: Map<GameId, List<Puzzle>> = emptyMap()) : TrainerPuzzleSource {

    override fun puzzles(game: GameId): Flow<Puzzle> = byGame[game].orEmpty().asFlow()
}

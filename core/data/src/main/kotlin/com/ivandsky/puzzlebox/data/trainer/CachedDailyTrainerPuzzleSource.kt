package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * The dailies already captured on the device, newest day first. It reads the day list and each cached puzzle and
 * nothing else: never `day`, never `refreshToday`, so no round touches the network. A day whose puzzle is no longer
 * cached is skipped.
 */
internal class CachedDailyTrainerPuzzleSource(private val daily: DailyRepository) : TrainerPuzzleSource {

    override fun puzzles(game: GameId): Flow<Puzzle> = flow {
        for (entry in daily.observeHistory(game).first()) {
            daily.cachedPuzzle(game, entry.day)?.let { emit(it) }
        }
    }
}

package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.database.TrainerPuzzleDao
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * What is already on the device: the cached dailies newest day first, then the puzzles the Trainer fetched into
 * its own cache (`trainer_puzzles`) newest fetch first. It reads the day list, each cached puzzle and the trainer
 * rows and nothing else: never `day`, never `refreshToday`, never a source, so no round touches the network. A day
 * whose puzzle is no longer cached, and a trainer row that no longer decodes, is skipped.
 */
internal class CachedTrainerPuzzleSource(
    private val daily: DailyRepository,
    private val trainerPuzzles: TrainerPuzzleDao,
    private val registry: GameRegistry,
) : TrainerPuzzleSource {

    override fun puzzles(game: GameId): Flow<Puzzle> = flow {
        for (entry in daily.observeHistory(game).first()) {
            daily.cachedPuzzle(game, entry.day)?.let { emit(it) }
        }
        val definition = registry[game]?.definition ?: return@flow
        val rows = trainerPuzzles.numbers(game.value).mapNotNull { trainerPuzzles.get(game.value, it) }
        for (row in rows.sortedByDescending { it.fetchedAtMs }) {
            row.toPuzzle(definition)?.let { emit(it) }
        }
    }
}

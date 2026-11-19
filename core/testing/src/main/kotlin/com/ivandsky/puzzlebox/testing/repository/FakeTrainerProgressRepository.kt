package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressRepository
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [TrainerProgressRepository]: [stored] is the persisted state, for assertions. */
class FakeTrainerProgressRepository : TrainerProgressRepository {

    private val state = MutableStateFlow<Map<GameId, Map<TechniqueId, TechniqueProgress>>>(emptyMap())

    val stored: Map<GameId, Map<TechniqueId, TechniqueProgress>> get() = state.value

    override fun observe(game: GameId): Flow<Map<TechniqueId, TechniqueProgress>> = state.map { it[game].orEmpty() }.distinctUntilChanged()

    override suspend fun update(
        game: GameId,
        technique: TechniqueId,
        transform: (TechniqueProgress) -> TechniqueProgress,
    ): TechniqueProgress {
        var result = TechniqueProgress.NEW
        state.update { all ->
            val forGame = all[game].orEmpty()
            result = transform(forGame[technique] ?: TechniqueProgress.NEW)
            all + (game to (forGame + (technique to result)))
        }
        return result
    }
}

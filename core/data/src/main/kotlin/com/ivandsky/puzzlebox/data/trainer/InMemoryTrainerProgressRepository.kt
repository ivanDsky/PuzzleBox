package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The dark-build [TrainerProgressRepository]: progress lives in memory and is lost on process
 * death. The Room table (ticket 09, #1629) replaces it behind the same Koin binding.
 */
class InMemoryTrainerProgressRepository : TrainerProgressRepository {

    private val state = MutableStateFlow<Map<GameId, Map<TechniqueId, TechniqueProgress>>>(emptyMap())

    override fun observe(game: GameId): Flow<Map<TechniqueId, TechniqueProgress>> =
        state.map { it[game].orEmpty() }.distinctUntilChanged()

    override suspend fun update(
        game: GameId,
        technique: TechniqueId,
        transform: (TechniqueProgress) -> TechniqueProgress,
    ): TechniqueProgress {
        var stored = TechniqueProgress.NEW
        state.update { all ->
            val forGame = all[game].orEmpty()
            stored = transform(forGame[technique] ?: TechniqueProgress.NEW)
            all + (game to (forGame + (technique to stored)))
        }
        return stored
    }
}

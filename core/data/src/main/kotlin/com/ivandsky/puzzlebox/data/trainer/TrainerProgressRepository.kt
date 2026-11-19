package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow

/** The trainer's per-game, per-technique progress store. */
interface TrainerProgressRepository {

    /** The progress of [game]'s techniques; a technique never answered is absent. */
    fun observe(game: GameId): Flow<Map<TechniqueId, TechniqueProgress>>

    /**
     * Atomically replaces [technique]'s progress in [game] with [transform] of the stored value
     * ([TechniqueProgress.NEW] when absent). Returns the stored value.
     */
    suspend fun update(
        game: GameId,
        technique: TechniqueId,
        transform: (TechniqueProgress) -> TechniqueProgress,
    ): TechniqueProgress
}

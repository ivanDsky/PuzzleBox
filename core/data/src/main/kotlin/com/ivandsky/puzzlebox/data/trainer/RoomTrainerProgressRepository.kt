package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.database.TrainerProgressDao
import com.ivandsky.puzzlebox.database.TrainerProgressEntity
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Room adapter of [TrainerProgressRepository] over `trainer_progress` ([dao]). Reads show the visible owner's rows
 * plus ownerless ones (re-querying when the owner changes); a technique held in two such rows, a signed-out one and
 * the owner's before the sync claims it, reads as their [TrainerProgressMerge], so the screen never shows the lower
 * value. A write goes to the [OwnerScope.writer]'s own row; the read-transform-write is one transaction, so two
 * answers recorded at once both land. A stored mark this version does not know reads as [Mark.New].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RoomTrainerProgressRepository(
    private val dao: TrainerProgressDao,
    private val owners: OwnerScope,
) : TrainerProgressRepository {

    override fun observe(game: GameId): Flow<Map<TechniqueId, TechniqueProgress>> =
        owners.visible.flatMapLatest { dao.observe(game.value, it) }.map { it.merged() }.distinctUntilChanged()

    override suspend fun update(
        game: GameId,
        technique: TechniqueId,
        transform: (TechniqueProgress) -> TechniqueProgress,
    ): TechniqueProgress {
        val writer = owners.writer()
        val written = dao.change(game.value, technique.value, owners.visibleNow()) { visible ->
            val current = visible.merged()[technique] ?: TechniqueProgress.NEW
            transform(current).toEntity(writer, game, technique)
        }
        return written.toProgress()
    }
}

private fun List<TrainerProgressEntity>.merged(): Map<TechniqueId, TechniqueProgress> =
    fold(emptyMap()) { all, row -> TrainerProgressMerge.merge(all, mapOf(TechniqueId(row.techniqueId) to row.toProgress())) }

internal fun TrainerProgressEntity.toProgress() = TechniqueProgress(mark.toMark(), inRow, answeredAtMs)

internal fun TechniqueProgress.toEntity(ownerUid: String?, game: GameId, technique: TechniqueId) = TrainerProgressEntity(
    ownerUid = ownerUid,
    gameId = game.value,
    techniqueId = technique.value,
    mark = mark.stored(),
    inRow = inRow,
    answeredAtMs = answeredAtMs,
)

// The stored words are persisted and later wire data: named here, never taken from the enum's name. The trainer
// cloud kind reuses them, so a mark means the same word in Room and in the cloud doc.
internal fun Mark.stored(): String = when (this) {
    Mark.New -> "new"
    Mark.Practicing -> "practicing"
    Mark.Mastered -> "mastered"
}

internal fun String.toMark(): Mark = when (this) {
    "practicing" -> Mark.Practicing
    "mastered" -> Mark.Mastered
    else -> Mark.New
}

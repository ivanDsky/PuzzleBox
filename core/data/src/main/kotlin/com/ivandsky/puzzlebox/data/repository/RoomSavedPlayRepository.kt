package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.database.SavedPlayDao
import com.ivandsky.puzzlebox.database.SavedPlayEntity
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Room adapter of [SavedPlayRepository]: each save is stamped with [clock] (epoch ms) and
 * prunes to the newest [CAP] slots in the same transaction (SC8), so the save just made
 * always survives. [PlaySlot.day] is stored as its ISO-8601 string.
 */
internal class RoomSavedPlayRepository(
    private val dao: SavedPlayDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : SavedPlayRepository {

    override suspend fun save(slot: PlaySlot, blob: ByteArray, elapsedMs: Long) {
        val row = SavedPlayEntity(
            gameId = slot.gameId.value,
            day = slot.day.toString(),
            blob = blob,
            elapsedMs = elapsedMs,
            updatedAt = clock(),
        )
        dao.saveAndPrune(row, CAP)
    }

    override suspend fun load(slot: PlaySlot): ByteArray? = dao.blob(slot.gameId.value, slot.day.toString())

    override suspend fun delete(slot: PlaySlot) {
        dao.delete(slot.gameId.value, slot.day.toString())
    }

    override fun observeInProgress(): Flow<Map<PlaySlot, Long>> =
        dao.observeSlots()
            .map { rows -> rows.associate { PlaySlot(GameId(it.gameId), LocalDate.parse(it.day)) to it.elapsedMs } }
            .distinctUntilChanged()

    private companion object {
        const val CAP = 8
    }
}

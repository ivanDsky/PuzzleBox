package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.database.CustomSavedPlayDao
import com.ivandsky.puzzlebox.database.CustomSavedPlayEntity
import com.ivandsky.puzzlebox.database.LegSavedPlayDao
import com.ivandsky.puzzlebox.database.LegSavedPlayEntity
import com.ivandsky.puzzlebox.database.SavedPlayDao
import com.ivandsky.puzzlebox.database.SavedPlayEntity
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Room adapter of [SavedPlayRepository]: a [PlaySlot.Daily] lives in `saved_plays` ([dao]),
 * a [PlaySlot.Custom] in `custom_saved_plays` ([customDao]), a [PlaySlot.Leg] in
 * `leg_saved_plays` ([legDao]). Each save is stamped with [clock] (epoch ms), always later than
 * this process's previous stamp, and prunes in the same transaction, so the save just made always
 * survives, even after the wall clock was stepped back: dailies to the
 * newest [CAP] per game, friend puzzles and duel puzzles each to the newest [CAP] of their own
 * table — no kind of save ever evicts another kind. [PlaySlot.Daily.day] is stored as its
 * ISO-8601 string.
 */
internal class RoomSavedPlayRepository(
    private val dao: SavedPlayDao,
    private val customDao: CustomSavedPlayDao,
    private val legDao: LegSavedPlayDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : SavedPlayRepository {

    private val lastStamp = AtomicLong(Long.MIN_VALUE)

    override suspend fun save(slot: PlaySlot, blob: ByteArray, elapsedMs: Long) {
        when (slot) {
            is PlaySlot.Daily -> dao.saveAndPrune(
                SavedPlayEntity(
                    gameId = slot.gameId.value,
                    day = slot.day.toString(),
                    blob = blob,
                    elapsedMs = elapsedMs,
                    updatedAt = stamp(),
                ),
                CAP,
            )
            is PlaySlot.Custom -> customDao.saveAndPrune(
                CustomSavedPlayEntity(
                    puzzleId = slot.puzzleId,
                    gameId = slot.gameId.value,
                    blob = blob,
                    elapsedMs = elapsedMs,
                    updatedAt = stamp(),
                ),
                CAP,
            )
            is PlaySlot.Leg -> legDao.saveAndPrune(
                LegSavedPlayEntity(
                    matchCode = slot.matchCode,
                    legIndex = slot.legIndex,
                    gameId = slot.gameId.value,
                    blob = blob,
                    elapsedMs = elapsedMs,
                    updatedAt = stamp(),
                ),
                CAP,
            )
        }
    }

    override suspend fun load(slot: PlaySlot): ByteArray? = when (slot) {
        is PlaySlot.Daily -> dao.blob(slot.gameId.value, slot.day.toString())
        is PlaySlot.Custom -> customDao.blob(slot.puzzleId)
        is PlaySlot.Leg -> legDao.blob(slot.matchCode, slot.legIndex)
    }

    override suspend fun delete(slot: PlaySlot) {
        when (slot) {
            is PlaySlot.Daily -> dao.delete(slot.gameId.value, slot.day.toString())
            is PlaySlot.Custom -> customDao.delete(slot.puzzleId)
            is PlaySlot.Leg -> legDao.delete(slot.matchCode, slot.legIndex)
        }
    }

    /** [clock], or one past the previous stamp when the clock reads earlier (a wall clock stepped back). */
    private fun stamp(): Long = lastStamp.updateAndGet { maxOf(clock(), it + 1) }

    override fun observeInProgress(): Flow<Map<PlaySlot, Long>> =
        combine(dao.observeSlots(), customDao.observeSlots(), legDao.observeSlots()) { dailies, customs, legs ->
            buildMap<PlaySlot, Long> {
                dailies.forEach { put(PlaySlot.Daily(GameId(it.gameId), LocalDate.parse(it.day)), it.elapsedMs) }
                customs.forEach { put(PlaySlot.Custom(GameId(it.gameId), it.puzzleId), it.elapsedMs) }
                legs.forEach { put(PlaySlot.Leg(GameId(it.gameId), it.matchCode, it.legIndex), it.elapsedMs) }
            }
        }.distinctUntilChanged()

    private companion object {
        const val CAP = 8
    }
}

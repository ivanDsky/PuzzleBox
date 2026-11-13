package com.ivandsky.puzzlebox.database.finalboard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Final boards, one header plus its entries per (gameId, day). */
@Dao
interface FinalBoardDao {

    @Query("SELECT * FROM final_boards WHERE gameId = :gameId AND day = :day")
    suspend fun header(gameId: String, day: String): FinalBoardEntity?

    @Query("SELECT * FROM final_board_entries WHERE gameId = :gameId AND day = :day ORDER BY uid")
    suspend fun entries(gameId: String, day: String): List<FinalBoardEntryEntity>

    /** Replaces the whole board of the header's (gameId, day): old header and entries go, new ones land, or nothing changes. */
    @Transaction
    suspend fun put(header: FinalBoardEntity, entries: List<FinalBoardEntryEntity>) {
        delete(header.gameId, header.day)
        insertHeader(header)
        insertEntries(entries)
    }

    // Delete + insert, not REPLACE/upsert: removing the header cascades its stale entries away.
    @Query("DELETE FROM final_boards WHERE gameId = :gameId AND day = :day")
    suspend fun delete(gameId: String, day: String)

    @Insert
    suspend fun insertHeader(header: FinalBoardEntity)

    @Insert
    suspend fun insertEntries(entries: List<FinalBoardEntryEntity>)
}

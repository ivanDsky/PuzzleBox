package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The log of what was already read from LinkedIn: one entry per game day, item and profile. */
@Dao
interface FetchLogDao {

    /** Every entry of one game day for one profile, re-emitted on change. */
    @Query("SELECT * FROM fetch_log WHERE game = :game AND day = :day AND profileId = :profileId")
    fun observe(game: String, day: String, profileId: String): Flow<List<FetchLogEntity>>

    /** [observe] for every day of one game from [from] to [to], both included. */
    @Query("SELECT * FROM fetch_log WHERE game = :game AND day BETWEEN :from AND :to AND profileId = :profileId")
    fun observeBetween(game: String, from: String, to: String, profileId: String): Flow<List<FetchLogEntity>>

    /** When [profileId] last had anything read, across every game day; null when nothing was. */
    @Query("SELECT MAX(fetchedAtMs) FROM fetch_log WHERE profileId = :profileId")
    fun observeLastFetchedAt(profileId: String): Flow<Long?>

    /** The last successful read of one item, or null when it was never read. */
    @Query("SELECT * FROM fetch_log WHERE game = :game AND day = :day AND item = :item AND profileId = :profileId")
    suspend fun get(game: String, day: String, item: String, profileId: String): FetchLogEntity?

    /** Records a successful read; a newer read of the same item replaces the older entry. */
    @Upsert
    suspend fun put(entry: FetchLogEntity)

    /** Forgets that the board of one game day was read, for every profile, so the day can be read again. */
    @Query("DELETE FROM fetch_log WHERE game = :game AND day = :day AND item = 'Board'")
    suspend fun deleteBoardEntries(game: String, day: String)

    @Query("DELETE FROM fetch_log")
    suspend fun clear()
}

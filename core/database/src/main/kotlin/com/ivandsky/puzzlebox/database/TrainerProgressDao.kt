package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Trainer progress reads and writes. Reads take the visible owner and see its rows plus ownerless ones (ADR-0029);
 * a technique may therefore come back in two rows (a signed-out one and the owner's, before the sync claims it), and
 * the caller merges them. Every write marks its row not uploaded.
 */
@Dao
interface TrainerProgressDao {

    /** Every row of [gameId] that [owner] sees. */
    @Query("SELECT * FROM trainer_progress WHERE gameId = :gameId AND $VISIBLE")
    fun observe(gameId: String, owner: String?): Flow<List<TrainerProgressEntity>>

    /** The rows of one technique that [owner] sees, oldest first. */
    @Query("SELECT * FROM trainer_progress WHERE gameId = :gameId AND techniqueId = :techniqueId AND $VISIBLE ORDER BY id")
    suspend fun visibleRows(gameId: String, techniqueId: String, owner: String?): List<TrainerProgressEntity>

    /** [owner]'s own row of one technique, exactly: a null [owner] finds the ownerless row only. */
    @Query(
        "SELECT * FROM trainer_progress " +
            "WHERE gameId = :gameId AND techniqueId = :techniqueId AND ownerUid IS :owner ORDER BY id LIMIT 1",
    )
    suspend fun find(gameId: String, techniqueId: String, owner: String?): TrainerProgressEntity?

    @Insert
    suspend fun insert(row: TrainerProgressEntity): Long

    @Update
    suspend fun update(row: TrainerProgressEntity)

    /**
     * Writes [row] as the one row of its (owner, game, technique): over the stored row, keeping its id, or as a new
     * row. In one transaction, so two writers cannot both insert; the row is marked not uploaded.
     */
    @Transaction
    suspend fun upsert(row: TrainerProgressEntity) {
        val stored = find(row.gameId, row.techniqueId, row.ownerUid)
        if (stored == null) {
            insert(row.copy(id = 0, uploaded = false))
        } else {
            update(row.copy(id = stored.id, uploaded = false))
        }
    }

    /**
     * Reads the rows of one technique that [owner] sees, hands them to [next] and [upsert]s the row it returns
     * (the caller stamps the writer's uid on it), all in one transaction so two changes of one technique never lose
     * a write. Returns the row written.
     */
    @Transaction
    suspend fun change(
        gameId: String,
        techniqueId: String,
        owner: String?,
        next: (visible: List<TrainerProgressEntity>) -> TrainerProgressEntity,
    ): TrainerProgressEntity {
        val row = next(visibleRows(gameId, techniqueId, owner))
        upsert(row)
        return row
    }
}

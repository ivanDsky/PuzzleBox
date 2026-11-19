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
 * the caller merges them. A row turns not uploaded only when it is new or its mark or in-a-row count changes: an answer
 * that moves only [TrainerProgressEntity.answeredAtMs] keeps the flag, because that stamp alone is not worth a cloud
 * write. The sync's own queries ([pendingGames], [adopt], [restore], [markUploaded]) work on the exact (owner, game,
 * technique) row and take no visible owner.
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
     * row. In one transaction, so two writers cannot both insert. The row is not uploaded when it is new or its mark or
     * in-a-row count differs from the stored row's; otherwise it keeps the stored flag, so an answer that changes only
     * `answeredAtMs` never makes the row pending.
     */
    @Transaction
    suspend fun upsert(row: TrainerProgressEntity) {
        val stored = find(row.gameId, row.techniqueId, row.ownerUid)
        if (stored == null) {
            insert(row.copy(id = 0, uploaded = false))
        } else {
            val unchanged = stored.mark == row.mark && stored.inRow == row.inRow
            update(row.copy(id = stored.id, uploaded = stored.uploaded && unchanged))
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

    /** Every row of [owner] in each game that has a row not yet uploaded: the whole game, since its cloud doc is whole. */
    @Query(
        "SELECT * FROM trainer_progress WHERE ownerUid = :owner AND gameId IN " +
            "(SELECT gameId FROM trainer_progress WHERE ownerUid = :owner AND uploaded = 0) ORDER BY gameId, techniqueId",
    )
    fun observePendingGames(owner: String): Flow<List<TrainerProgressEntity>>

    /** What [observePendingGames] reads now. */
    @Query(
        "SELECT * FROM trainer_progress WHERE ownerUid = :owner AND gameId IN " +
            "(SELECT gameId FROM trainer_progress WHERE ownerUid = :owner AND uploaded = 0) ORDER BY gameId, techniqueId",
    )
    suspend fun pendingGames(owner: String): List<TrainerProgressEntity>

    /** Every row [owner] holds in [gameId], pending or not. */
    @Query("SELECT * FROM trainer_progress WHERE ownerUid = :owner AND gameId = :gameId ORDER BY techniqueId")
    suspend fun ownedRowsOfGame(owner: String, gameId: String): List<TrainerProgressEntity>

    /** The rows nobody owned when written, which the first account to sign in folds into its own. */
    @Query("SELECT * FROM trainer_progress WHERE ownerUid IS NULL ORDER BY id")
    suspend fun ownerlessRows(): List<TrainerProgressEntity>

    @Query("DELETE FROM trainer_progress WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Writes [row] over the stored row of its exact (owner, game, technique), or as a new one, keeping the [row]'s own
     * `uploaded` flag: the sync's write, unlike [upsert], which decides the flag itself.
     */
    @Transaction
    suspend fun restore(row: TrainerProgressEntity) {
        val stored = find(row.gameId, row.techniqueId, row.ownerUid)
        if (stored == null) insert(row.copy(id = 0)) else update(row.copy(id = stored.id))
    }

    /**
     * Folds every ownerless row into [owner]'s row of the same (game, technique) and deletes it: [merge] gets the
     * owner's row (null when it has none) and the ownerless one and returns the value to keep. The result is written
     * for [owner] and pending, since the cloud has not seen the merged value. One transaction, so a signed-out row is
     * never lost or counted twice if the app dies midway.
     */
    @Transaction
    suspend fun adopt(
        owner: String,
        merge: (owned: TrainerProgressEntity?, ownerless: TrainerProgressEntity) -> TrainerProgressEntity,
    ) {
        for (loose in ownerlessRows()) {
            val owned = find(loose.gameId, loose.techniqueId, owner)
            restore(merge(owned, loose).copy(ownerUid = owner, uploaded = false))
            delete(loose.id)
        }
    }

    /**
     * Marks [owner]'s row of one technique uploaded only while it still holds exactly the value that was sent, so a
     * write between the push and its acknowledgement keeps the row pending. Returns the rows marked (0 or 1).
     */
    @Query(
        "UPDATE trainer_progress SET uploaded = 1 WHERE ownerUid = :owner AND gameId = :gameId AND " +
            "techniqueId = :techniqueId AND mark = :mark AND inRow = :inRow AND answeredAtMs = :answeredAtMs",
    )
    suspend fun markUploaded(
        owner: String,
        gameId: String,
        techniqueId: String,
        mark: String,
        inRow: Int,
        answeredAtMs: Long,
    ): Int

    /** Clears [owner]'s uploaded flags, so the next pass sends every game again. */
    @Query("UPDATE trainer_progress SET uploaded = 0 WHERE ownerUid = :owner")
    suspend fun clearUploaded(owner: String)
}

package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.database.DraftDao
import com.ivandsky.puzzlebox.database.DraftEntity
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Room adapter of [DraftRepository] over `drafts` ([dao]). [Draft.updatedAt] is stored as epoch
 * ms; [Draft.testSolve] is spread over the three nullable test columns and read back only when
 * all three are present.
 */
internal class RoomDraftRepository(private val dao: DraftDao) : DraftRepository {

    // Room re-runs both queries on every `drafts` write, including other drafts' autosaves.
    override fun observeAll(): Flow<List<Draft>> =
        dao.observeAll().map { rows -> rows.map { it.toDraft() } }.distinctUntilChanged()

    override fun observe(id: String): Flow<Draft?> = dao.observe(id).map { it?.toDraft() }.distinctUntilChanged()

    override suspend fun get(id: String): Draft? = dao.get(id)?.toDraft()

    override suspend fun upsert(draft: Draft) = dao.upsert(draft.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}

private fun Draft.toEntity() = DraftEntity(
    id = id,
    gameId = gameId.value,
    variant = variant.value,
    draftFormatVersion = draftFormatVersion,
    blob = blob,
    title = title,
    updatedAt = updatedAt.toEpochMilli(),
    testLayoutKey = testSolve?.layoutKey,
    testTimeMs = testSolve?.timeMs,
    testHints = testSolve?.hints,
    reservedCode = reservedCode,
    imported = imported,
    authorName = authorName,
    note = note,
)

private fun DraftEntity.toDraft(): Draft {
    val layoutKey = testLayoutKey
    val timeMs = testTimeMs
    val hints = testHints
    return Draft(
        id = id,
        gameId = GameId(gameId),
        variant = VariantId(variant),
        draftFormatVersion = draftFormatVersion,
        blob = blob,
        title = title,
        updatedAt = Instant.ofEpochMilli(updatedAt),
        testSolve = if (layoutKey != null && timeMs != null && hints != null) {
            TestSolve(layoutKey, timeMs, hints)
        } else {
            null
        },
        reservedCode = reservedCode,
        imported = imported,
        authorName = authorName,
        note = note,
    )
}

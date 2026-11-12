package com.ivandsky.puzzlebox.data.repository

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.database.DraftDao
import com.ivandsky.puzzlebox.database.DraftEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.SyncTombstoneDao
import com.ivandsky.puzzlebox.database.SyncTombstoneEntity
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** The cloud collection of drafts; a tombstone's `kind`. */
private const val DRAFTS_KIND = "drafts"

/** A tombstone waits this much past the undo window, so an Undo tap at the very end still wins. */
private const val TOMBSTONE_MARGIN_MS = 5_000L

/**
 * Room adapter of [DraftRepository] over `drafts` ([dao]). [Draft.updatedAt] is stored as epoch
 * ms; [Draft.testSolve] is spread over the three nullable test columns and read back only when
 * all three are present.
 *
 * Cloud sync (ADR-0029): readers see the [owners] visible owner's drafts plus ownerless ones; a
 * write stamps the [OwnerScope.writer] uid on a draft that has no owner yet and keeps the owner and
 * upload marker of a stored row. Deleting an owned draft writes a `sync_tombstones` row in [tombstones]
 * (due after [DRAFT_UNDO_WINDOW_MS] plus a margin, stamped by [clock]) in the same transaction; an
 * upsert, which is how Undo restores the draft, clears it. A draft nobody owns was never uploaded and
 * leaves no tombstone.
 */
internal class RoomDraftRepository(
    private val db: PuzzleBoxDatabase,
    private val dao: DraftDao,
    private val tombstones: SyncTombstoneDao,
    private val owners: OwnerScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : DraftRepository {

    // Room re-runs both queries on every `drafts` write, including other drafts' autosaves.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAll(): Flow<List<Draft>> = owners.visible
        .flatMapLatest { owner -> dao.observeAll(owner) }
        .map { rows -> rows.map { it.toDraft() } }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(id: String): Flow<Draft?> = owners.visible
        .flatMapLatest { owner -> dao.observe(id, owner) }
        .map { it?.toDraft() }
        .distinctUntilChanged()

    override suspend fun get(id: String): Draft? = dao.get(id, owners.visible.value)?.toDraft()

    override suspend fun upsert(draft: Draft) {
        db.withTransaction {
            val stored = dao.find(draft.id)
            val owner = stored?.ownerUid ?: owners.writer()
            dao.upsert(draft.toEntity(owner, stored?.uploadedFingerprint))
            if (owner != null) tombstones.delete(DRAFTS_KIND, draft.id, owner)
        }
    }

    override suspend fun delete(id: String) {
        db.withTransaction {
            val owner = dao.find(id)?.ownerUid
            dao.delete(id)
            if (owner != null) {
                val now = clock()
                tombstones.insertOrReplace(
                    SyncTombstoneEntity(DRAFTS_KIND, id, owner, now, now + DRAFT_UNDO_WINDOW_MS + TOMBSTONE_MARGIN_MS),
                )
            }
        }
    }
}

private fun Draft.toEntity(ownerUid: String?, uploadedFingerprint: String?) = DraftEntity(
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
    ownerUid = ownerUid,
    uploadedFingerprint = uploadedFingerprint,
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

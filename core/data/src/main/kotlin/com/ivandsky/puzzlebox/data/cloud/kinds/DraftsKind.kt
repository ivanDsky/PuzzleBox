package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.database.DraftDao
import com.ivandsky.puzzlebox.database.DraftEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.SyncTombstoneDao
import com.ivandsky.puzzlebox.database.SyncTombstoneEntity
import com.ivandsky.puzzlebox.model.ShareCode
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

private const val DRAFTS = "drafts"

/** The rules cap a draft document's blob here (ADR-0029 A9); a bigger draft is never uploaded, so it cannot trip a `Denied`. */
internal const val MAX_BLOB_BYTES = 65_536

/**
 * Drafts as a synced kind (ADR-0029 A6, A17): one doc per draft id, `{formatVersion, gameId, variant,
 * draftFormatVersion, name, blob, editedAtMs}` and, once set, `reservedCode`, or the tombstone `{formatVersion,
 * deleted, deletedAtMs}` of a delete whose undo window closed. `name` is the draft's title and `editedAtMs` its
 * `updatedAt`; the test solve and the publish details stay on the device. The reserved code travels so a draft kept
 * under a published code is hidden from the Drafts tab on the next device too; it is never cleared once set.
 *
 * A row is pending when its `drafts` row belongs to the uid and its content fingerprint (a hash of
 * exactly the doc's content) differs from `uploadedFingerprint`. The merge rule lives here: the later edit wins by
 * `editedAtMs` (a newer doc without a `reservedCode` keeps the local one, an older doc with one gives it to a local row
 * that has none), a tombstone beats an edit up to its `deletedAtMs` and loses to a later one, and a local row the cloud
 * has no newer word on is never overwritten or deleted, so a draft made while the first restore runs survives. A
 * merge-set tombstone over a live doc (or a live doc over a tombstone) leaves both shapes' keys; the later of
 * `editedAtMs` and `deletedAtMs` says which one the doc is.
 */
internal class DraftsKind(
    private val db: PuzzleBoxDatabase,
    private val drafts: DraftDao,
    private val tombstones: SyncTombstoneDao,
    private val clock: Clock,
) : SyncedKind {

    override val kind: String = DRAFTS

    /**
     * Fires on a change to the uid's drafts or draft tombstones, and again when the next unsent tombstone's undo
     * window closes, since nothing else changes then.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun pendingIds(uid: String): Flow<Set<String>> =
        combine(drafts.observeOwnedBy(uid), drafts.observeUnsentTombstoneIds(uid)) { rows, _ -> rows }
            .transformLatest { rows ->
                val edited = rows.filter { it.isPending() }.mapTo(mutableSetOf()) { it.id }
                while (true) {
                    val now = clock.millis()
                    val unsent = unsentTombstones(uid)
                    emit(edited + unsent.filter { it.eligibleAtMs <= now }.map { it.docId })
                    val wake = unsent.minOfOrNull { it.eligibleAtMs }?.takeIf { it > now } ?: break
                    delay(wake - now)
                }
            }
            .distinctUntilChanged()

    override suspend fun adopt(uid: String) {
        drafts.adopt(uid)
    }

    override suspend fun pending(uid: String): List<CloudDoc> {
        val now = clock.millis()
        val edits = drafts.ownedBy(uid).filter { it.isPending() }.map { CloudDoc(it.id, it.toFields()) }
        val deletes = unsentTombstones(uid).filter { it.eligibleAtMs <= now }.map { CloudDoc(it.docId, it.toFields()) }
        return edits + deletes
    }

    override suspend fun apply(uid: String, docs: List<CloudDoc>): Int = db.withTransaction {
        val unsent = unsentTombstones(uid).associateBy { it.docId }
        var restored = 0
        for (doc in docs) {
            val incoming = DraftDoc.parse(doc) ?: continue
            val stored = drafts.find(doc.id)
            if (stored?.ownerUid != null && stored.ownerUid != uid) continue
            when (incoming) {
                is DraftDoc.Gone -> when {
                    stored == null -> Unit
                    stored.updatedAt <= incoming.deletedAtMs -> drafts.delete(doc.id)
                    else -> keepLocal(stored, incoming.fingerprint)
                }
                is DraftDoc.Edit -> {
                    val deleted = unsent[doc.id]
                    if (deleted != null) {
                        if (incoming.editedAtMs <= deleted.deletedAtMs) continue
                        tombstones.delete(DRAFTS, doc.id, uid)
                    }
                    if (stored == null || incoming.editedAtMs > stored.updatedAt) {
                        drafts.upsert(incoming.toEntity(doc.id, uid, stored))
                        restored++
                    } else {
                        keepLocal(adoptCode(stored, incoming), incoming.fingerprint)
                    }
                }
            }
        }
        restored
    }

    override suspend fun acknowledge(uid: String, uploaded: List<CloudDoc>) = db.withTransaction {
        val unsent = unsentTombstones(uid).associateBy { it.docId }
        for (doc in uploaded) {
            when (val sent = DraftDoc.parse(doc) ?: continue) {
                is DraftDoc.Gone -> if (unsent[doc.id]?.deletedAtMs == sent.deletedAtMs) {
                    tombstones.markUploaded(DRAFTS, doc.id, uid)
                }
                is DraftDoc.Edit -> {
                    val stored = drafts.find(doc.id)
                    if (stored?.ownerUid == uid && stored.fingerprint() == sent.fingerprint) {
                        drafts.setUploadedFingerprint(doc.id, sent.fingerprint)
                    }
                }
            }
        }
    }

    override suspend fun forget(uid: String) = db.withTransaction {
        drafts.clearUploadedFingerprints(uid)
        tombstones.clearUploaded(uid)
    }

    private suspend fun unsentTombstones(uid: String): List<SyncTombstoneEntity> =
        tombstones.pendingEligible(uid, Long.MAX_VALUE).filter { it.kind == DRAFTS }

    /** The local row won but has no reserved code, and the doc has one: the row takes it and stays otherwise as it is. */
    private suspend fun adoptCode(stored: DraftEntity, incoming: DraftDoc.Edit): DraftEntity {
        if (stored.reservedCode != null || incoming.reservedCode == null) return stored
        return stored.withReservedCode(incoming.reservedCode).also { drafts.upsert(it) }
    }

    /** The local row won; when it is marked uploaded but the cloud holds something else, it must go up again. */
    private suspend fun keepLocal(stored: DraftEntity, cloudFingerprint: String?) {
        val local = stored.fingerprint()
        if (stored.uploadedFingerprint == local && local != cloudFingerprint) {
            drafts.setUploadedFingerprint(stored.id, null)
        }
    }
}

private fun DraftEntity.isPending(): Boolean = blob.size <= MAX_BLOB_BYTES && fingerprint() != uploadedFingerprint

private fun DraftEntity.fingerprint(): String =
    fingerprintOf(gameId, variant, draftFormatVersion, title, updatedAt, blob, reservedCode)

private fun DraftEntity.toFields(): Map<String, Any?> {
    val fields = mutableMapOf<String, Any?>(
        "formatVersion" to CLOUD_FORMAT_VERSION,
        "gameId" to gameId,
        "variant" to variant,
        "draftFormatVersion" to draftFormatVersion.toLong(),
        "name" to title,
        "blob" to blob,
        "editedAtMs" to updatedAt,
    )
    reservedCode?.let { fields["reservedCode"] = it }
    return fields
}

private fun DraftEntity.withReservedCode(code: String) = DraftEntity(
    id = id,
    gameId = gameId,
    variant = variant,
    draftFormatVersion = draftFormatVersion,
    blob = blob,
    title = title,
    updatedAt = updatedAt,
    testLayoutKey = testLayoutKey,
    testTimeMs = testTimeMs,
    testHints = testHints,
    reservedCode = code,
    imported = imported,
    authorName = authorName,
    note = note,
    ownerUid = ownerUid,
    uploadedFingerprint = uploadedFingerprint,
)

private fun SyncTombstoneEntity.toFields(): Map<String, Any?> =
    mapOf("formatVersion" to CLOUD_FORMAT_VERSION, "deleted" to true, "deletedAtMs" to deletedAtMs)

private fun DraftDoc.Edit.toEntity(id: String, uid: String, stored: DraftEntity?): DraftEntity {
    // The blob is replaced, so a test solve of the old one is gone; the local-only fields stay.
    val sameBlob = stored != null && stored.blob.contentEquals(blob)
    return DraftEntity(
        id = id,
        gameId = gameId,
        variant = variant,
        draftFormatVersion = draftFormatVersion,
        blob = blob,
        title = name,
        updatedAt = editedAtMs,
        testLayoutKey = stored?.testLayoutKey.takeIf { sameBlob },
        testTimeMs = stored?.testTimeMs.takeIf { sameBlob },
        testHints = stored?.testHints.takeIf { sameBlob },
        // A code never goes back to null: a doc without one leaves the local code.
        reservedCode = reservedCode ?: stored?.reservedCode,
        imported = stored?.imported ?: false,
        authorName = stored?.authorName ?: "",
        note = stored?.note ?: "",
        ownerUid = uid,
        uploadedFingerprint = fingerprint,
    )
}

/** A draft doc as read from the cloud: a live edit, or the delete that ended it. */
private sealed interface DraftDoc {
    class Edit(
        val gameId: String,
        val variant: String,
        val draftFormatVersion: Int,
        val name: String,
        val blob: ByteArray,
        val editedAtMs: Long,
        val reservedCode: String?,
    ) : DraftDoc {
        val fingerprint: String =
            fingerprintOf(gameId, variant, draftFormatVersion, name, editedAtMs, blob, reservedCode)
    }

    class Gone(val deletedAtMs: Long) : DraftDoc {
        /** A tombstone is never a local row's content, so no row ever matches it. */
        val fingerprint: String? = null
    }

    companion object {
        /** The doc's shape, or null for a doc that is neither a complete edit nor a tombstone. */
        fun parse(doc: CloudDoc): DraftDoc? {
            val f = doc.fields
            val deletedAt = (f["deletedAtMs"] as? Number)?.toLong()?.takeIf { f["deleted"] == true }
            val edit = edit(f)
            return when {
                edit != null && (deletedAt == null || edit.editedAtMs > deletedAt) -> edit
                deletedAt != null -> Gone(deletedAt)
                else -> null
            }
        }

        private fun edit(f: Map<String, Any?>): Edit? {
            val gameId = f["gameId"] as? String ?: return null
            val variant = f["variant"] as? String ?: return null
            val format = (f["draftFormatVersion"] as? Number)?.toInt() ?: return null
            val name = f["name"] as? String ?: return null
            val blob = f["blob"] as? ByteArray ?: return null
            val editedAtMs = (f["editedAtMs"] as? Number)?.toLong() ?: return null
            // A code the rules would not accept is not trusted either.
            val code = (f["reservedCode"] as? String)?.takeIf { ShareCode.parse(it)?.value == it }
            return Edit(gameId, variant, format, name, blob, editedAtMs, code)
        }
    }
}

/** Hex SHA-256 over exactly the content a draft doc carries; a draft without a reserved code hashes as before it existed. */
private fun fingerprintOf(
    gameId: String,
    variant: String,
    draftFormatVersion: Int,
    name: String,
    editedAtMs: Long,
    blob: ByteArray,
    reservedCode: String?,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (text in listOf(gameId, variant, name)) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }
    val numbers = ByteBuffer.allocate(Int.SIZE_BYTES + Long.SIZE_BYTES).putInt(draftFormatVersion).putLong(editedAtMs)
    digest.update(numbers.array())
    digest.update(blob)
    reservedCode?.let { digest.update(it.toByteArray(Charsets.UTF_8)) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

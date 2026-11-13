package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.ReceivedPuzzleDao
import com.ivandsky.puzzlebox.database.ReceivedPuzzleEntity
import com.ivandsky.puzzlebox.database.SyncTombstoneDao
import com.ivandsky.puzzlebox.database.SyncTombstoneEntity
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Friends' puzzles as the synced kind `received` (ADR-0029): one doc per share code. The payload bytes travel, so a
 * restore never depends on the public document. A live doc holds what opens the puzzle offline and what its card
 * shows; the device-local `postAttempts` and `unpublished` stay out.
 *
 * A row is pending while its `uploadedFingerprint` is null or differs from its `postState`: that is the only synced
 * field `updatePost` changes, and `upsert` (the only other writer) clears the fingerprint. A delete is a
 * `sync_tombstones` row, eligible at once, sent as `{deleted: true, deletedAtMs}`. Every live doc says
 * `deleted = false`, because writes are merge-sets and a puzzle opened again after a delete must override the
 * tombstone's `deleted = true`.
 */
internal class ReceivedKind(
    private val db: PuzzleBoxDatabase,
    private val received: ReceivedPuzzleDao,
    private val tombstones: SyncTombstoneDao,
    private val clock: Clock,
) : SyncedKind {

    override val kind: String = KIND

    override fun pendingIds(uid: String): Flow<Set<String>> =
        received.observePendingCodes(uid).map { it.toSet() }.distinctUntilChanged()

    override suspend fun adopt(uid: String) = received.adopt(uid)

    override suspend fun pending(uid: String): List<CloudDoc> = db.withTransaction {
        received.dropReopenedTombstones(uid)
        val rows = received.pendingUpload(uid).map { it.toDoc() }
        val deletes = tombstones.pendingEligible(uid, clock.millis()).filter { it.kind == KIND }.map { it.toDoc() }
        rows + deletes
    }

    override suspend fun apply(uid: String, docs: List<CloudDoc>): Int = db.withTransaction {
        var restored = 0
        for (doc in docs) {
            val incoming = decode(uid, doc) ?: continue
            val code = incoming.code
            val local = received.find(code)
            when (val step = mergeReceived(uid, local, received.tombstonedAt(code, uid), incoming)) {
                ReceivedMerge.Keep -> Unit
                ReceivedMerge.DeleteLocal -> received.delete(code)
                is ReceivedMerge.Insert -> {
                    received.upsert(step.row)
                    tombstones.delete(KIND, code, uid)
                    restored++
                }
                is ReceivedMerge.PostForward -> {
                    received.updatePost(code, step.state, local!!.postAttempts)
                    received.markUploaded(code, uid, local.openedAt, step.state)
                }
            }
        }
        restored
    }

    override suspend fun acknowledge(uid: String, uploaded: List<CloudDoc>) = db.withTransaction {
        for (doc in uploaded) {
            if (doc.fields["deleted"] == true) {
                tombstones.markUploaded(KIND, doc.id, uid)
            } else {
                val openedAt = (doc.fields["receivedAtMs"] as? Number)?.toLong() ?: continue
                val state = doc.fields["postState"] as? String ?: continue
                received.markUploaded(doc.id, uid, openedAt, state)
            }
        }
    }

    override suspend fun forget(uid: String) = db.withTransaction {
        received.clearUploaded(uid)
        received.clearTombstonesUploaded(uid)
    }

    private companion object {
        const val KIND = "received"
    }
}

/** What a doc says, decoded; `Live` carries the row to insert: the cloud's values, owned by the uid, already uploaded. */
internal sealed interface IncomingReceived {
    val code: String

    data class Live(val row: ReceivedPuzzleEntity) : IncomingReceived {
        override val code: String get() = row.code
    }

    data class Gone(override val code: String, val deletedAtMs: Long) : IncomingReceived
}

/** The outcome of merging one doc into the local row for its code. */
internal sealed interface ReceivedMerge {
    data object Keep : ReceivedMerge
    data object DeleteLocal : ReceivedMerge
    data class Insert(val row: ReceivedPuzzleEntity) : ReceivedMerge
    data class PostForward(val state: String) : ReceivedMerge
}

/**
 * The merge rule for one doc (A17), pure. [local] is the row for the code whoever owns it, [tombstonedAtMs] when [uid]
 * deleted the code on this device. Another account's row is never touched. A tombstone wins over the live row unless
 * the puzzle was opened after the delete; the same holds for a local tombstone over a live doc. An unknown code
 * inserts with the cloud's values, post state included, so a puzzle posted on another device is not posted again. A
 * known code keeps every local value except a post state that moved forward (Pending, LocalOnly, Posted).
 */
internal fun mergeReceived(
    uid: String,
    local: ReceivedPuzzleEntity?,
    tombstonedAtMs: Long?,
    incoming: IncomingReceived,
): ReceivedMerge {
    if (local != null && local.ownerUid != null && local.ownerUid != uid) return ReceivedMerge.Keep
    return when (incoming) {
        is IncomingReceived.Gone ->
            if (local == null || local.openedAt > incoming.deletedAtMs) ReceivedMerge.Keep else ReceivedMerge.DeleteLocal
        is IncomingReceived.Live -> when {
            local == null ->
                if (tombstonedAtMs != null && tombstonedAtMs >= incoming.row.openedAt) {
                    ReceivedMerge.Keep
                } else {
                    ReceivedMerge.Insert(incoming.row)
                }
            postRank(incoming.row.postState) > postRank(local.postState) ->
                ReceivedMerge.PostForward(incoming.row.postState)
            else -> ReceivedMerge.Keep
        }
    }
}

private fun postRank(state: String): Int = when (state) {
    PostState.Posted.name -> 2
    PostState.LocalOnly.name -> 1
    else -> 0
}

private fun ReceivedPuzzleEntity.toDoc(): CloudDoc {
    val fields = mutableMapOf<String, Any?>(
        "formatVersion" to CLOUD_FORMAT_VERSION,
        "gameId" to gameId,
        "variant" to variant,
        "sharedFormatVersion" to sharedFormatVersion.toLong(),
        "name" to title,
        "senderName" to authorName,
        "authorUid" to authorUid,
        "note" to note,
        "authorTimeMs" to authorTimeMs,
        "authorHints" to authorHints.toLong(),
        "receivedAtMs" to openedAt,
        "bytes" to puzzleBytes,
        "postState" to postState,
        "deleted" to false,
    )
    difficulty?.let { fields["difficulty"] = it }
    return CloudDoc(code, fields)
}

private fun SyncTombstoneEntity.toDoc() =
    CloudDoc(docId, mapOf("formatVersion" to CLOUD_FORMAT_VERSION, "deleted" to true, "deletedAtMs" to deletedAtMs))

/** Null for a doc that cannot be used: a code that does not parse, or a live doc missing what opens the puzzle. */
private fun decode(uid: String, doc: CloudDoc): IncomingReceived? {
    val code = ShareCode.parse(doc.id)?.value ?: return null
    val f = doc.fields
    if (f["deleted"] == true) {
        val deletedAtMs = f.long("deletedAtMs") ?: return null
        return IncomingReceived.Gone(code, deletedAtMs)
    }
    val state = f["postState"] as? String ?: return null
    // A name this version does not know was written by a newer app.
    val post = PostState.entries.firstOrNull { it.name == state } ?: PostState.Pending
    return IncomingReceived.Live(
        ReceivedPuzzleEntity(
            code = code,
            gameId = f.text("gameId") ?: return null,
            variant = f.text("variant") ?: return null,
            sharedFormatVersion = f.long("sharedFormatVersion")?.toInt() ?: return null,
            puzzleBytes = f["bytes"] as? ByteArray ?: return null,
            title = f.text("name") ?: return null,
            authorName = f.text("senderName") ?: return null,
            note = f.text("note").orEmpty(),
            authorTimeMs = f.long("authorTimeMs") ?: 0L,
            authorHints = f.long("authorHints")?.toInt() ?: 0,
            authorUid = f.text("authorUid").orEmpty(),
            openedAt = f.long("receivedAtMs") ?: return null,
            unpublished = false,
            postState = post.name,
            postAttempts = 0,
            difficulty = f.text("difficulty"),
            ownerUid = uid,
            // The cloud already holds this state.
            uploadedFingerprint = post.name,
        ),
    )
}

private fun Map<String, Any?>.text(key: String): String? = this[key] as? String

private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

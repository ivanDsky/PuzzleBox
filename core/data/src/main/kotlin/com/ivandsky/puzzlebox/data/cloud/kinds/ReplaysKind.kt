package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.database.CompletionReplayDao
import com.ivandsky.puzzlebox.database.CompletionReplayEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.model.SolveReplay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Solve replays as the synced kind `replays` (ADR-0029 A1, A6, A43): one doc per completion, named by the completion's
 * `syncId`, `{formatVersion: 1, replay: bytes}` with the replay at most [SolveReplay.MAX_BYTES] (the rules cap it
 * there too). The local table is keyed by the completion's row id, so every query goes through `completions` and
 * through its owner: a replay of another account's or an ownerless completion is never pending, and a replay row only
 * exists beside a finished game's completion, so a game still being played never has one.
 *
 * A replay is pending while its `uploaded` marker is 0. A replay never changes once stored, so there is no merge rule:
 * a restored doc is stored (already marked uploaded) only when the completion exists for the account and is the
 * record row or the newest row of its puzzle, the rows the History screen can open, and never replaces a replay the
 * device already has. Everything else in the doc is dropped, so storage keeps the same record-plus-latest bound the
 * local prune enforces. Replays have no owner of their own: they follow their completion, so [adopt] has nothing to do.
 */
internal class ReplaysKind(
    private val db: PuzzleBoxDatabase,
    private val replays: CompletionReplayDao,
) : SyncedKind {

    override val kind: String = "replays"

    override fun pendingIds(uid: String): Flow<Set<String>> =
        replays.observePendingSyncIds(uid).map { it.toSet() }.distinctUntilChanged()

    override suspend fun adopt(uid: String) = Unit

    override suspend fun pending(uid: String): List<CloudDoc> =
        replays.pending(uid)
            .filter { SolveReplay.of(it.replay) != null }
            .map { CloudDoc(it.syncId, mapOf("formatVersion" to 1L, "replay" to it.replay)) }

    override suspend fun apply(uid: String, docs: List<CloudDoc>): Int = db.withTransaction {
        var restored = 0
        for (doc in docs) {
            val bytes = (doc.fields["replay"] as? ByteArray)?.takeIf { SolveReplay.of(it) != null } ?: continue
            val completionId = replays.keptCompletionId(uid, doc.id) ?: continue
            if (replays.insertIfAbsent(CompletionReplayEntity(completionId, bytes, uploaded = true)) != IGNORED) restored++
        }
        restored
    }

    override suspend fun acknowledge(uid: String, uploaded: List<CloudDoc>) = db.withTransaction {
        uploaded.map { it.id }.chunked(CHUNK).forEach { replays.markUploaded(uid, it) }
    }

    override suspend fun forget(uid: String) = replays.clearUploaded(uid)

    private companion object {
        /** Ids per SQL statement, under SQLite's bound-variable limit on every supported Android version. */
        const val CHUNK = 500

        /** What an ignored insert returns. */
        const val IGNORED = -1L
    }
}

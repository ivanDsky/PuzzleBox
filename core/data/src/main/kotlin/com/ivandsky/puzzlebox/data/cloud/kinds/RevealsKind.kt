package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.database.MatchLocalDao
import com.ivandsky.puzzlebox.database.MatchLocalEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Seen duel reveals as the synced kind `reveals` (#1542): one doc per duel, named by its share code,
 * `{formatVersion: 1, seenAtMs, myScore?, theirScore?}`, the scores absent for a reveal seen before they were kept. So a
 * reveal seen on one device of the account is seen on all of them, with the scores of its first reveal.
 *
 * The first reveal wins everywhere. A reveal never changes once stamped, so a row is pending only until its
 * `revealUploaded` marker is set, and the rules refuse a doc that would change one already in the cloud. A restored doc
 * stamps a row that has no reveal yet and never replaces one the device already has; either way the row is then marked
 * as in the cloud, so this device never uploads over the account's first reveal. Self duels never leave the device:
 * their reveals are never pending. Only seen reveals travel; an invite opened or dismissed stays this device's.
 */
internal class RevealsKind(
    private val db: PuzzleBoxDatabase,
    private val dao: MatchLocalDao,
) : SyncedKind {

    override val kind: String = "reveals"

    override fun pendingIds(uid: String): Flow<Set<String>> =
        dao.observePendingReveals(uid).map { it.toSet() }.distinctUntilChanged()

    override suspend fun adopt(uid: String) = dao.claimOwnerlessReveals(uid)

    override suspend fun pending(uid: String): List<CloudDoc> =
        dao.pendingReveals(uid).map { row -> CloudDoc(row.code, row.toFields()) }

    override suspend fun apply(uid: String, docs: List<CloudDoc>): Int = db.withTransaction {
        var restored = 0
        for (doc in docs) {
            val code = ShareCode.parse(doc.id)?.value?.takeIf { it == doc.id } ?: continue
            val seenAtMs = (doc.fields["seenAtMs"] as? Long)?.takeIf { it >= 0 } ?: continue
            if (doc.fields["formatVersion"] != CLOUD_FORMAT_VERSION) continue
            dao.insertIfAbsent(MatchLocalEntity(code, LocalRole.Participant.name, null, openedAt = seenAtMs, revealSeenAt = null))
            restored += dao.markRevealSeen(code, seenAtMs, doc.score("myScore"), doc.score("theirScore"), uid)
            dao.markRevealInCloud(uid, code)
        }
        restored
    }

    override suspend fun acknowledge(uid: String, uploaded: List<CloudDoc>) = db.withTransaction {
        uploaded.map { it.id }.chunked(CHUNK).forEach { dao.markRevealsUploaded(uid, it) }
    }

    override suspend fun forget(uid: String) = dao.clearRevealsUploaded(uid)

    private fun MatchLocalEntity.toFields(): Map<String, Any?> = buildMap {
        put("formatVersion", CLOUD_FORMAT_VERSION)
        put("seenAtMs", checkNotNull(revealSeenAt))
        revealMyScore?.let { put("myScore", it.toLong()) }
        revealTheirScore?.let { put("theirScore", it.toLong()) }
    }

    /** A puzzle count of one side, at most a duel's legs; anything else reads as a reveal without scores. */
    private fun CloudDoc.score(key: String): Int? = (fields[key] as? Long)?.takeIf { it in 0..Match.MAX_LEGS }?.toInt()

    private companion object {
        /** Ids per SQL statement, under SQLite's bound-variable limit on every supported Android version. */
        const val CHUNK = 500
    }
}

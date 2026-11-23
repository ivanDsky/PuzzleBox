package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressMerge
import com.ivandsky.puzzlebox.data.trainer.stored
import com.ivandsky.puzzlebox.data.trainer.toEntity
import com.ivandsky.puzzlebox.data.trainer.toMark
import com.ivandsky.puzzlebox.data.trainer.toProgress
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.TrainerProgressDao
import com.ivandsky.puzzlebox.database.TrainerProgressEntity
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Trainer progress as the synced kind `trainer` (epic trainer, #1635): one doc per game, named by the game id,
 * `{formatVersion: 1, techniques}` (the cloud adapter adds `updatedAt`), where `techniques` maps a technique id to
 * `{mark: "practicing" | "mastered", inRow, answeredAtMs}` and a technique still new is absent. The doc is the whole game,
 * so a technique changed on one device and another changed on a second both survive a merge.
 *
 * The merge is [TrainerProgressMerge], per technique, on both sides: a received doc is merged into the local rows, the
 * higher mark winning and the latest answer setting the count. A technique whose merged value the cloud lacks stays
 * pending (or turns pending), so the value that won travels back; one equal to the cloud's is stored as uploaded. A
 * row is pending only while its mark or count changed since it was last in the cloud ([TrainerProgressDao.upsert]),
 * so an answer that moves only `answeredAtMs` never costs a cloud write. A game is pending while any of its rows is,
 * and its doc then carries every non-new row of the account, not only the changed ones.
 *
 * Signing in folds the signed-out player's rows into the account's, one row per (game, technique) (`adopt`). The marks
 * travel as the words the repository stores, never as enum names. A received value that does not fit (an unknown mark,
 * a missing or negative number, a technique that is not a map, a doc without a `techniques` map or with another
 * `formatVersion`) skips that technique or doc and never throws.
 *
 * The kind always syncs: `cloudSyncModule` binds it like the other kinds.
 */
class TrainerProgressKind internal constructor(
    private val db: PuzzleBoxDatabase,
    private val dao: TrainerProgressDao,
) : SyncedKind {

    override val kind: String = TRAINER

    override fun pendingIds(uid: String): Flow<Set<String>> =
        pendingStamps(uid).map { it.keys.toSet() }.distinctUntilChanged()

    /**
     * Each pending game with a fingerprint of all its rows' content, so an answer recorded while the game is pending
     * is a change the runner sees. The flag is not part of it: an acknowledgement leaves the fingerprint alone.
     */
    override fun pendingStamps(uid: String): Flow<Map<String, String>> =
        dao.observePendingGames(uid).map { rows ->
            rows.groupBy { it.gameId }.mapValues { (_, game) -> game.fingerprint() }
        }.distinctUntilChanged()

    override suspend fun adopt(uid: String) = dao.adopt(uid) { owned, ownerless ->
        if (owned == null) {
            ownerless
        } else {
            val technique = TechniqueId(ownerless.techniqueId)
            val merged = TrainerProgressMerge.merge(
                mapOf(technique to owned.toProgress()),
                mapOf(technique to ownerless.toProgress()),
            ).getValue(technique)
            merged.toEntity(uid, GameId(ownerless.gameId), technique)
        }
    }

    override suspend fun pending(uid: String): List<CloudDoc> =
        dao.pendingGames(uid).groupBy { it.gameId }.map { (game, rows) -> CloudDoc(game, rows.toFields()) }

    override suspend fun apply(uid: String, docs: List<CloudDoc>): Int = db.withTransaction {
        var restored = 0
        for (doc in docs) {
            val remote = doc.decode() ?: continue
            if (remote.techniques.isEmpty()) continue
            val stored = dao.ownedRowsOfGame(uid, remote.game).associateBy { it.techniqueId }
            val merged = TrainerProgressMerge.merge(
                stored.entries.associate { (technique, row) -> TechniqueId(technique) to row.toProgress() },
                remote.techniques,
            )
            for ((technique, theirs) in remote.techniques) {
                val value = merged.getValue(technique)
                val row = stored[technique.value]
                // Equal to the cloud's value: nothing to send. Otherwise this device knows something the cloud lacks.
                val inCloud = value == theirs
                val valueChanged = row == null || row.toProgress() != value
                if (valueChanged || row?.uploaded != inCloud) {
                    dao.restore(value.toEntity(uid, GameId(remote.game), technique).copy(uploaded = inCloud))
                }
                if (valueChanged) restored++
            }
        }
        restored
    }

    /**
     * Marks each sent technique's row uploaded while it still holds the value that was sent (a write between the push
     * and this call keeps it pending), and the game's rows that are still new, which the doc leaves out and so can
     * never be sent.
     */
    override suspend fun acknowledge(uid: String, uploaded: List<CloudDoc>) = db.withTransaction {
        for (doc in uploaded) {
            val sent = doc.decode() ?: continue
            for ((technique, progress) in sent.techniques) {
                dao.markUploaded(uid, sent.game, technique.value, progress.mark.stored(), progress.inRow, progress.answeredAtMs)
            }
            dao.ownedRowsOfGame(uid, sent.game).filter { it.mark.toMark() == Mark.New }.forEach {
                dao.markUploaded(uid, it.gameId, it.techniqueId, it.mark, it.inRow, it.answeredAtMs)
            }
        }
    }

    override suspend fun forget(uid: String) = dao.clearUploaded(uid)

    private fun List<TrainerProgressEntity>.toFields(): Map<String, Any?> = mapOf(
        "formatVersion" to CLOUD_FORMAT_VERSION,
        "techniques" to filter { it.mark.toMark() != Mark.New }.associate { row ->
            row.techniqueId to mapOf(
                "mark" to row.mark.toMark().stored(),
                "inRow" to row.inRow.toLong(),
                "answeredAtMs" to row.answeredAtMs,
            )
        },
    )

    private fun List<TrainerProgressEntity>.fingerprint(): String =
        sortedBy { it.techniqueId }.joinToString("|") { "${it.techniqueId}:${it.mark}:${it.inRow}:${it.answeredAtMs}" }

    /** A game's techniques as a doc carries them. */
    private class Decoded(val game: String, val techniques: Map<TechniqueId, TechniqueProgress>)

    /** The doc's game and its valid techniques; null for a doc that is not read at all. */
    private fun CloudDoc.decode(): Decoded? {
        if (!GAME_ID.matches(id) || fields["formatVersion"] != CLOUD_FORMAT_VERSION) return null
        val techniques = fields["techniques"] as? Map<*, *> ?: return null
        val valid = buildMap {
            for ((key, value) in techniques) {
                val technique = (key as? String)?.takeIf { TECHNIQUE_ID.matches(it) } ?: continue
                val progress = (value as? Map<*, *>)?.toProgress() ?: continue
                put(TechniqueId(technique), progress)
            }
        }
        return Decoded(id, valid)
    }

    /** One technique's value, or null when its mark is not one a doc carries or a number is missing or out of range. */
    private fun Map<*, *>.toProgress(): TechniqueProgress? {
        val mark = (this["mark"] as? String)?.toMark()?.takeIf { it != Mark.New } ?: return null
        val inRow = (this["inRow"] as? Long)?.takeIf { it in 0..Int.MAX_VALUE } ?: return null
        val answeredAtMs = (this["answeredAtMs"] as? Long)?.takeIf { it >= 0 } ?: return null
        return TechniqueProgress(mark, inRow.toInt(), answeredAtMs)
    }

    private companion object {
        const val TRAINER = "trainer"

        /** The doc id and technique key shapes `firestore.rules` accepts for the kind. */
        val GAME_ID = Regex("[a-z0-9]{1,32}")
        val TECHNIQUE_ID = Regex("[a-z0-9-]{1,40}")
    }
}

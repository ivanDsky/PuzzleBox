package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.database.PublishedPuzzleDao
import com.ivandsky.puzzlebox.database.PublishedPuzzleEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val KIND = "published"
private const val FORMAT_VERSION = 1L

/**
 * The published list as a synced kind (ADR-0029, A16): one doc per share code, `{formatVersion, gameId, name,
 * publishedAtMs, unpublished}`. A row is pending while its [PublishedPuzzleEntity.uploadedFingerprint] differs from
 * the [fingerprint] of what its doc would hold now; the fingerprint is stored on acknowledge and on restore, and the
 * repository's upsert resets it. The public puzzle document stays canonical, so the variant, the author's test
 * solve, post-state and solve counts never travel; a restored row gets empty values for them.
 * [mergePublished] holds the merge rule.
 */
internal class PublishedKind(
    private val db: PuzzleBoxDatabase,
    private val dao: PublishedPuzzleDao,
) : SyncedKind {

    override val kind: String = KIND

    override fun pendingIds(uid: String): Flow<Set<String>> = dao.observeOwned(uid)
        .map { rows -> rows.filter { it.isPending() }.map { it.code }.toSet() }
        .distinctUntilChanged()

    override suspend fun adopt(uid: String) = dao.adopt(uid)

    override suspend fun pending(uid: String): List<CloudDoc> =
        dao.owned(uid).filter { it.isPending() }.map { CloudDoc(it.code, it.toDoc().fields()) }

    override suspend fun apply(uid: String, docs: List<CloudDoc>): Int = db.withTransaction {
        var restored = 0
        for (cloud in docs) {
            if (ShareCode.parse(cloud.id)?.value != cloud.id) continue
            val remote = cloud.toDoc() ?: continue
            val merged = mergePublished(dao.find(cloud.id), cloud.id, remote, uid) ?: continue
            if (merged.changed) dao.upsert(merged.row)
            if (merged.restored) restored++
        }
        restored
    }

    override suspend fun acknowledge(uid: String, uploaded: List<CloudDoc>) = db.withTransaction {
        for (cloud in uploaded) {
            val sent = cloud.toDoc() ?: continue
            val row = dao.find(cloud.id)?.takeIf { it.ownerUid == uid } ?: continue
            // A row edited while the upload ran differs from what was sent and stays pending.
            if (row.toDoc() == sent) dao.setFingerprint(row.code, uid, sent.fingerprint())
        }
    }

    override suspend fun forget(uid: String) = dao.clearFingerprints(uid)

    private fun PublishedPuzzleEntity.isPending() = uploadedFingerprint != toDoc().fingerprint()
}

/** What travels for one published code. */
internal data class PublishedDoc(
    val gameId: String,
    val name: String,
    val publishedAtMs: Long,
    val unpublished: Boolean,
) {
    fun fields(): Map<String, Any?> = mapOf(
        "formatVersion" to FORMAT_VERSION,
        "gameId" to gameId,
        "name" to name,
        "publishedAtMs" to publishedAtMs,
        "unpublished" to unpublished,
    )

    /** Equal for equal docs; stored on the row once the cloud holds this version. */
    fun fingerprint(): String = listOf(gameId, name, publishedAtMs, unpublished).joinToString(SEPARATOR)

    private companion object {
        /** The ASCII unit separator, which no title or game id holds. */
        const val SEPARATOR = "\u001F"
    }
}

/** The outcome of merging one doc: the [row] to hold, whether it [changed] the stored one and was [restored]. */
internal class PublishedMerge(val row: PublishedPuzzleEntity, val changed: Boolean, val restored: Boolean)

/**
 * The merge rule of one doc [remote] for [code] into the stored row [local] (null when the code is unknown), for
 * account [uid]:
 * - an unknown code inserts a row owned by [uid], marked uploaded;
 * - a row of another account is left alone (null);
 * - otherwise the local fields win, except that `unpublished` is one-way: it is true once either side has it.
 * A row that equals the doc after the merge is marked uploaded, so it does not go straight back up.
 */
internal fun mergePublished(
    local: PublishedPuzzleEntity?,
    code: String,
    remote: PublishedDoc,
    uid: String,
): PublishedMerge? {
    if (local == null) {
        val row = PublishedPuzzleEntity(
            code = code,
            gameId = remote.gameId,
            variant = "",
            title = remote.name,
            authorTimeMs = 0,
            createdAt = remote.publishedAtMs,
            unpublished = remote.unpublished,
            solvesCount = null,
            solvesCountAt = null,
            authorHints = 0,
            ownerUid = uid,
            uploadedFingerprint = remote.fingerprint(),
        )
        return PublishedMerge(row, changed = true, restored = true)
    }
    if (local.ownerUid != null && local.ownerUid != uid) return null
    val unpublished = local.unpublished || remote.unpublished
    val merged = local.toDoc().copy(unpublished = unpublished)
    val fingerprint = if (merged == remote) remote.fingerprint() else local.uploadedFingerprint
    val owner = local.ownerUid ?: uid
    val flipped = unpublished != local.unpublished
    if (!flipped && fingerprint == local.uploadedFingerprint && owner == local.ownerUid) {
        return PublishedMerge(local, changed = false, restored = false)
    }
    val row = PublishedPuzzleEntity(
        code = local.code,
        gameId = local.gameId,
        variant = local.variant,
        title = local.title,
        authorTimeMs = local.authorTimeMs,
        createdAt = local.createdAt,
        unpublished = unpublished,
        solvesCount = local.solvesCount,
        solvesCountAt = local.solvesCountAt,
        authorHints = local.authorHints,
        ownerUid = owner,
        uploadedFingerprint = fingerprint,
    )
    return PublishedMerge(row, changed = true, restored = flipped)
}

private fun PublishedPuzzleEntity.toDoc() = PublishedDoc(gameId, title, createdAt, unpublished)

/** The doc held by this cloud doc, or null when a field is missing or mistyped or the format version is not 1. */
private fun CloudDoc.toDoc(): PublishedDoc? {
    if ((fields["formatVersion"] as? Number)?.toLong() != FORMAT_VERSION) return null
    return PublishedDoc(
        gameId = fields["gameId"] as? String ?: return null,
        name = fields["name"] as? String ?: return null,
        publishedAtMs = (fields["publishedAtMs"] as? Number)?.toLong() ?: return null,
        unpublished = fields["unpublished"] as? Boolean ?: return null,
    )
}

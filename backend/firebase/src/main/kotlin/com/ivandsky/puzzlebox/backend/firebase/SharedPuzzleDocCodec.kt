package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.model.ShareCode
import java.util.Base64

/**
 * `puzzles/{code}` document ↔ [SharedPuzzleDoc]. The code is the document id, so it is not a
 * field. The payload travels as base64 text and `createdAt` is stamped by the server: the
 * caller's [SharedPuzzleDoc.createdAtMs] is never written.
 */
internal object SharedPuzzleDocCodec {

    const val FORMAT_VERSION = SharedPuzzleDoc.FORMAT_VERSION

    const val FIELD_FORMAT_VERSION = "formatVersion"
    const val FIELD_GAME_ID = "gameId"
    const val FIELD_VARIANT = "variant"
    const val FIELD_SHARE_CODEC_VERSION = "shareCodecVersion"
    const val FIELD_PAYLOAD = "payload"
    const val FIELD_TITLE = "title"
    const val FIELD_AUTHOR_NAME = "authorName"
    const val FIELD_NOTE = "note"
    const val FIELD_AUTHOR_UID = "authorUid"
    const val FIELD_AUTHOR_TIME = "authorTimeMs"
    const val FIELD_AUTHOR_HINTS = "authorHints"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_UNPUBLISHED = "unpublished"

    fun encode(doc: SharedPuzzleDoc): Map<String, Any?> = mapOf(
        FIELD_FORMAT_VERSION to doc.formatVersion,
        FIELD_GAME_ID to doc.gameId,
        FIELD_VARIANT to doc.variant,
        FIELD_SHARE_CODEC_VERSION to doc.shareCodecVersion,
        FIELD_PAYLOAD to Base64.getEncoder().encodeToString(doc.payload),
        FIELD_TITLE to doc.title,
        FIELD_AUTHOR_NAME to doc.authorName,
        FIELD_NOTE to doc.note,
        FIELD_AUTHOR_UID to doc.authorUid,
        FIELD_AUTHOR_TIME to doc.authorTimeMs,
        FIELD_AUTHOR_HINTS to doc.authorHints,
        FIELD_CREATED_AT to FieldValue.serverTimestamp(),
        FIELD_UNPUBLISHED to doc.unpublished,
    )

    /**
     * Whether [stored] holds what [sent] would publish: every field a friend sees or plays.
     * The per-attempt `createdAt` and the `unpublished` flag are left out, and the author is
     * compared by the caller. Numbers compare by value, as the server returns them as longs.
     */
    fun sameContent(sent: Map<String, Any?>, stored: Map<String, Any?>): Boolean =
        CONTENT_FIELDS.all { comparable(sent[it]) == comparable(stored[it]) }

    private val CONTENT_FIELDS = listOf(
        FIELD_FORMAT_VERSION,
        FIELD_GAME_ID,
        FIELD_VARIANT,
        FIELD_SHARE_CODEC_VERSION,
        FIELD_PAYLOAD,
        FIELD_TITLE,
        FIELD_AUTHOR_NAME,
        FIELD_NOTE,
        FIELD_AUTHOR_TIME,
        FIELD_AUTHOR_HINTS,
    )

    private fun comparable(value: Any?): Any? = if (value is Number) value.toLong() else value

    /**
     * Any version but [FORMAT_VERSION] is [SharedFetchResult.UnsupportedVersion]: a newer format
     * may reinterpret known fields. A doc of this version that does not parse is
     * [SharedFetchResult.NotFound], since there is nothing a player could open.
     */
    fun decode(code: ShareCode, data: Map<String, Any?>): SharedFetchResult {
        val version = data.int(FIELD_FORMAT_VERSION) ?: return SharedFetchResult.NotFound
        if (version != FORMAT_VERSION) return SharedFetchResult.UnsupportedVersion(version)
        val doc = SharedPuzzleDoc(
            code = code,
            formatVersion = version,
            gameId = data.string(FIELD_GAME_ID) ?: return SharedFetchResult.NotFound,
            variant = data.string(FIELD_VARIANT) ?: return SharedFetchResult.NotFound,
            shareCodecVersion = data.int(FIELD_SHARE_CODEC_VERSION) ?: return SharedFetchResult.NotFound,
            payload = data.base64(FIELD_PAYLOAD) ?: return SharedFetchResult.NotFound,
            title = data.string(FIELD_TITLE) ?: return SharedFetchResult.NotFound,
            authorName = data.string(FIELD_AUTHOR_NAME) ?: return SharedFetchResult.NotFound,
            note = data.string(FIELD_NOTE) ?: return SharedFetchResult.NotFound,
            authorUid = data.string(FIELD_AUTHOR_UID) ?: return SharedFetchResult.NotFound,
            authorTimeMs = (data[FIELD_AUTHOR_TIME] as? Number)?.toLong() ?: return SharedFetchResult.NotFound,
            authorHints = data.int(FIELD_AUTHOR_HINTS) ?: return SharedFetchResult.NotFound,
            createdAtMs = (data[FIELD_CREATED_AT] as? Timestamp)?.toDate()?.time ?: return SharedFetchResult.NotFound,
            unpublished = data[FIELD_UNPUBLISHED] as? Boolean ?: return SharedFetchResult.NotFound,
        )
        return SharedFetchResult.Found(doc)
    }

    private fun Map<String, Any?>.string(field: String): String? = this[field] as? String

    private fun Map<String, Any?>.int(field: String): Int? = (this[field] as? Number)?.toInt()

    private fun Map<String, Any?>.base64(field: String): ByteArray? {
        val text = string(field) ?: return null
        return try {
            Base64.getDecoder().decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

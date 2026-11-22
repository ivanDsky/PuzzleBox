package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.ShareCode
import java.util.Base64

/**
 * `puzzles/{code}` document ↔ [SharedPuzzleDoc]. The code is the document id, so it is not a
 * field. The payload travels as base64 text and `createdAt` is stamped by the server: the
 * caller's [SharedPuzzleDoc.createdAtMs] is never written.
 *
 * `visibility` and `difficulty` are optional (ADR-0020 amendment). A link-only puzzle is written
 * without `visibility`, so it is the document every released client writes, and a doc without the
 * field, or with any word but `public`, reads as link only: a puzzle is never listed by
 * mistake. An unknown `difficulty` reads as none.
 *
 * `solves` (#2032) is the server's first-solve counter: never written here (a new puzzle starts without it; the
 * board's first entry bumps it), read as null when absent, mistyped or negative, and outside [sameContent].
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
    const val FIELD_VISIBILITY = "visibility"
    const val FIELD_DIFFICULTY = "difficulty"
    const val FIELD_SOLVES = "solves"

    const val VISIBILITY_PUBLIC = "public"

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
    ) + listOfNotNull(
        (FIELD_VISIBILITY to VISIBILITY_PUBLIC).takeIf { doc.visibility == Visibility.Public },
        doc.difficulty?.let { FIELD_DIFFICULTY to difficultyName(it) },
    )

    /**
     * Whether [stored] holds what [sent] would publish: every field a friend sees or plays, and
     * who can find it. The per-attempt `createdAt` and the `unpublished` flag are left out, and
     * the author is compared by the caller. Numbers compare by value, as the server returns them
     * as longs; the two optional fields compare as they read, so no `visibility` equals `link`.
     */
    fun sameContent(sent: Map<String, Any?>, stored: Map<String, Any?>): Boolean =
        CONTENT_FIELDS.all { comparable(sent[it]) == comparable(stored[it]) } &&
            visibility(sent) == visibility(stored) &&
            difficulty(sent) == difficulty(stored)

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

    private fun visibility(data: Map<String, Any?>): Visibility =
        if (data[FIELD_VISIBILITY] == VISIBILITY_PUBLIC) Visibility.Public else Visibility.LinkOnly

    private fun difficulty(data: Map<String, Any?>): Difficulty.Level? =
        Difficulty.Level.entries.firstOrNull { difficultyName(it) == data[FIELD_DIFFICULTY] }

    /** The word stored for [level]; the rules list the same four. */
    fun difficultyName(level: Difficulty.Level): String = when (level) {
        Difficulty.Level.Easy -> "easy"
        Difficulty.Level.Medium -> "medium"
        Difficulty.Level.Hard -> "hard"
        Difficulty.Level.Expert -> "expert"
    }

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
            visibility = visibility(data),
            difficulty = difficulty(data),
            solves = data.int(FIELD_SOLVES)?.takeIf { it >= 0 },
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

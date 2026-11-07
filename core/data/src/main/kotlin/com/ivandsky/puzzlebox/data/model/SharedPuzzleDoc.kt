package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.model.ShareCode

/**
 * One shared puzzle as stored under its [code] (ADR-0020). [payload] is the game's share codec
 * in [shareCodecVersion], with the solution stripped; [formatVersion] versions the document
 * itself (ADR-0005). [unpublished] hides it from new players without deleting it.
 * Equality compares [payload] by content, so a doc read back from the store equals the one written.
 */
data class SharedPuzzleDoc(
    val code: ShareCode,
    val formatVersion: Int,
    val gameId: String,
    val variant: String,
    val shareCodecVersion: Int,
    val payload: ByteArray,
    val title: String,
    val authorName: String,
    val note: String,
    val authorUid: String,
    val authorTimeMs: Long,
    val authorHints: Int,
    val createdAtMs: Long,
    val unpublished: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        other is SharedPuzzleDoc &&
            code == other.code &&
            formatVersion == other.formatVersion &&
            gameId == other.gameId &&
            variant == other.variant &&
            shareCodecVersion == other.shareCodecVersion &&
            payload.contentEquals(other.payload) &&
            title == other.title &&
            authorName == other.authorName &&
            note == other.note &&
            authorUid == other.authorUid &&
            authorTimeMs == other.authorTimeMs &&
            authorHints == other.authorHints &&
            createdAtMs == other.createdAtMs &&
            unpublished == other.unpublished

    override fun hashCode(): Int {
        var h = code.hashCode()
        h = 31 * h + formatVersion
        h = 31 * h + gameId.hashCode()
        h = 31 * h + variant.hashCode()
        h = 31 * h + shareCodecVersion
        h = 31 * h + payload.contentHashCode()
        h = 31 * h + title.hashCode()
        h = 31 * h + authorName.hashCode()
        h = 31 * h + note.hashCode()
        h = 31 * h + authorUid.hashCode()
        h = 31 * h + authorTimeMs.hashCode()
        h = 31 * h + authorHints
        h = 31 * h + createdAtMs.hashCode()
        h = 31 * h + unpublished.hashCode()
        return h
    }

    companion object {
        const val FORMAT_VERSION: Int = 1
    }
}

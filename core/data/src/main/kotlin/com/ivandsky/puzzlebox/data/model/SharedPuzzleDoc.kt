package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * One shared puzzle as stored under its [code] (ADR-0020). [payload] is the game's share codec
 * in [shareCodecVersion], with the solution stripped; [formatVersion] versions the document
 * itself (ADR-0005). [unpublished] hides it from new players without deleting it.
 * [visibility] says whether the community list shows it; a doc stored before the field existed is
 * link only. [difficulty] is the label the author's device computed, null when the puzzle needs
 * guessing or the game has no ladder. Both are fixed at publish.
 * [solves] is the server's first-solve counter (#2032), null while the doc has no such field (a puzzle no
 * counting client has solved yet, or one never backfilled); a reader treats null as unknown, not as zero.
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
    val visibility: Visibility = Visibility.LinkOnly,
    val difficulty: Difficulty.Level? = null,
    val solves: Int? = null,
) {
    /** Who can find a shared puzzle: [Public] ones are listed in the community, [LinkOnly] ones open by code or link only. */
    enum class Visibility { Public, LinkOnly }

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
            unpublished == other.unpublished &&
            visibility == other.visibility &&
            difficulty == other.difficulty &&
            solves == other.solves

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
        h = 31 * h + visibility.hashCode()
        h = 31 * h + difficulty.hashCode()
        h = 31 * h + solves.hashCode()
        return h
    }

    companion object {
        const val FORMAT_VERSION: Int = 1
    }
}

package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant

/**
 * One friend's puzzle opened on this device under [code], played or not. [puzzleBytes] is the
 * verified canonical Puzzle in shared format [sharedFormatVersion] (ADR-0005), including the
 * solution this device derived. [postState] tracks this user's board post, tried [postAttempts]
 * times; [unpublished] marks a code its author withdrew. Equality compares [puzzleBytes] by
 * content, so a row read back from storage equals the one saved.
 */
data class ReceivedPuzzle(
    val code: ShareCode,
    val gameId: GameId,
    val variant: VariantId,
    val sharedFormatVersion: Int,
    val puzzleBytes: ByteArray,          // verified canonical Puzzle incl. the solution derived on this device
    val title: String,
    val authorName: String,
    val note: String,
    val authorTimeMs: Long,
    val authorHints: Int,
    val authorUid: String,
    val openedAt: Instant,
    val unpublished: Boolean,
    val postState: PostState,
    val postAttempts: Int,
    val difficulty: Difficulty.Level?,   // from the local re-verify; null for games without a ladder
) {
    override fun equals(other: Any?): Boolean =
        other is ReceivedPuzzle &&
            code == other.code &&
            gameId == other.gameId &&
            variant == other.variant &&
            sharedFormatVersion == other.sharedFormatVersion &&
            puzzleBytes.contentEquals(other.puzzleBytes) &&
            title == other.title &&
            authorName == other.authorName &&
            note == other.note &&
            authorTimeMs == other.authorTimeMs &&
            authorHints == other.authorHints &&
            authorUid == other.authorUid &&
            openedAt == other.openedAt &&
            unpublished == other.unpublished &&
            postState == other.postState &&
            postAttempts == other.postAttempts &&
            difficulty == other.difficulty

    override fun hashCode(): Int {
        var h = code.hashCode()
        h = 31 * h + gameId.hashCode()
        h = 31 * h + variant.hashCode()
        h = 31 * h + sharedFormatVersion
        h = 31 * h + puzzleBytes.contentHashCode()
        h = 31 * h + title.hashCode()
        h = 31 * h + authorName.hashCode()
        h = 31 * h + note.hashCode()
        h = 31 * h + authorTimeMs.hashCode()
        h = 31 * h + authorHints
        h = 31 * h + authorUid.hashCode()
        h = 31 * h + openedAt.hashCode()
        h = 31 * h + unpublished.hashCode()
        h = 31 * h + postState.hashCode()
        h = 31 * h + postAttempts
        h = 31 * h + (difficulty?.hashCode() ?: 0)
        return h
    }
}

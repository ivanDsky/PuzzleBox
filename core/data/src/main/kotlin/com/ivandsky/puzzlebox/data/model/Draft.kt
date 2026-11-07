package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant

/**
 * One editor draft (A6): the opaque board [blob] in format [draftFormatVersion] (ADR-0005) for
 * one [variant] of one game. [testSolve] stays null until the author solves it; [reservedCode]
 * is the share code once reserved (M3); [imported] marks a draft copied in from elsewhere.
 * Equality compares [blob] by content, so a draft read back from storage equals the one saved.
 */
data class Draft(
    val id: String,
    val gameId: GameId,
    val variant: VariantId,
    val draftFormatVersion: Int,
    val blob: ByteArray,
    val title: String,
    val updatedAt: Instant,
    val testSolve: TestSolve?,
    val reservedCode: String?,   // M3 writes it
    val imported: Boolean,
    val authorName: String,      // M3 publish details; "" until set (≤ AUTHOR_MAX)
    val note: String,            // M3 publish details; "" until set (≤ NOTE_MAX)
) {
    override fun equals(other: Any?): Boolean =
        other is Draft &&
            id == other.id &&
            gameId == other.gameId &&
            variant == other.variant &&
            draftFormatVersion == other.draftFormatVersion &&
            blob.contentEquals(other.blob) &&
            title == other.title &&
            updatedAt == other.updatedAt &&
            testSolve == other.testSolve &&
            reservedCode == other.reservedCode &&
            imported == other.imported &&
            authorName == other.authorName &&
            note == other.note

    override fun hashCode(): Int {
        var h = id.hashCode()
        h = 31 * h + gameId.hashCode()
        h = 31 * h + variant.hashCode()
        h = 31 * h + draftFormatVersion
        h = 31 * h + blob.contentHashCode()
        h = 31 * h + title.hashCode()
        h = 31 * h + updatedAt.hashCode()
        h = 31 * h + (testSolve?.hashCode() ?: 0)
        h = 31 * h + (reservedCode?.hashCode() ?: 0)
        h = 31 * h + imported.hashCode()
        h = 31 * h + authorName.hashCode()
        h = 31 * h + note.hashCode()
        return h
    }

    companion object {
        const val TITLE_MAX: Int = 60    // A25: 1..60
        const val AUTHOR_MAX: Int = 40
        const val NOTE_MAX: Int = 140
    }
}

package com.ivandsky.puzzlebox.domain

/**
 * The outcome of [ImportDraftFromText]: [Imported] or one failure per way whole text can
 * fail, 1:1 with the non-parsed NotationParse cases. A failure writes nothing.
 */
sealed interface ImportResult {
    /** The imported draft row, already stored with `imported = true`. */
    data class Imported(val draftId: String) : ImportResult

    data class TooLarge(val bytes: Int, val line: Int, val col: Int) : ImportResult

    data class UnknownGame(val gameId: String) : ImportResult

    data class UnsupportedVersion(val found: Int, val maxKnown: Int) : ImportResult

    data class Malformed(val line: Int, val col: Int, val message: String) : ImportResult
}

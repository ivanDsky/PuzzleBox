package com.ivandsky.puzzlebox.game.editor

/** The outcome of [NotationText.parse]: one typed outcome per way whole text can fail (spec A23). */
sealed interface NotationParse {
    /** Fresh draft, empty history. */
    data class Parsed(val draft: EditorDraft<*>) : NotationParse

    /** [line]/[col]: 1-based position of the first character past the cap (spec success criterion 14). */
    data class TooLarge(val bytes: Int, val line: Int, val col: Int) : NotationParse

    /** No plugin for the id, no editor, or an editor without EditorNotation. */
    data class UnknownGame(val gameId: String) : NotationParse

    data class UnsupportedVersion(val gameId: String, val found: Int, val maxKnown: Int) : NotationParse

    /** Missing/garbled header (line of the first non-blank line, col 1) or the game's own NotationResult.Err. */
    data class Malformed(val line: Int, val col: Int, val message: String) : NotationParse
}

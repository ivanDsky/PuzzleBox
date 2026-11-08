package com.ivandsky.puzzlebox.game.editor

/** The game parser's outcome over blanked whole text: a draft, or a whole-text 1-based position. */
sealed interface NotationResult<out D> {
    data class Ok<D>(val draft: D) : NotationResult<D>
    data class Err(val line: Int, val col: Int, val message: String) : NotationResult<Nothing>
}

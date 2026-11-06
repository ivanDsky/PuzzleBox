package com.ivandsky.puzzlebox.games.strands

/**
 * The canonical puzzle (A1, A17): a [rows] × [cols] board of uppercase [letters] (row-major),
 * the verbatim [clue], the [spangram] and [themeWords] with their intended cells, and the
 * [validWords] a trace may spell (A16: solutions ∪ theme words ∪ spangram, uppercase).
 * [themeWords] keeps the feed order and never contains the spangram.
 */
data class StrandsPuzzle(
    val rows: Int,
    val cols: Int,
    val letters: String,
    val clue: String,
    val spangram: StrandsWord,
    val themeWords: List<StrandsWord>,
    val validWords: Set<String>,
    val constructors: String,
) {
    fun letterAt(c: Coord): Char = letters[c.row * cols + c.col]

    /** Theme words plus the spangram — the count completion needs (A5). */
    val totalWords: Int get() = themeWords.size + 1
}

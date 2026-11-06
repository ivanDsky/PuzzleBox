package com.ivandsky.puzzlebox.games.strands.feed.gate

import com.ivandsky.puzzlebox.games.strands.Coord

/** A [com.ivandsky.puzzlebox.games.strands.feed.StrandsWire] with every field the puzzle is built from present. */
class CompleteWire(
    val printDate: String,
    val board: List<String>,
    val spangram: String,
    val spangramCoords: List<List<Int>>,
    val themeWords: List<String>,
    val themeCoords: Map<String, List<List<Int>>>,
) {
    val rows: Int get() = board.size
    val cols: Int get() = board.firstOrNull()?.length ?: 0

    /** Each theme word's coords, then the spangram's; only meaningful once [WordPathsCheck] passed. */
    fun allWordCells(): List<Coord> =
        (themeWords.map { themeCoords.getValue(it) } + listOf(spangramCoords)).flatten().map { (row, col) -> Coord(row, col) }
}

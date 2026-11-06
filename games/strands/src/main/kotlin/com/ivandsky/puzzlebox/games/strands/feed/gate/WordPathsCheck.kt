package com.ivandsky.puzzlebox.games.strands.feed.gate

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.engine.StrandsPath
import java.time.LocalDate

/**
 * Every theme word and the spangram has one in-board `[row, col]` per letter, spelling the word,
 * forming a traceable [StrandsPath] — so building the puzzle from a trusted wire cannot fail.
 */
object WordPathsCheck : StrandsTrustCheck {

    override fun problem(wire: CompleteWire, requested: LocalDate): String? {
        pathProblem(wire.spangram, wire.spangramCoords, wire)?.let { return "spangram: $it" }
        wire.themeWords.forEach { word ->
            val coords = wire.themeCoords[word] ?: return "theme word $word has no coords"
            pathProblem(word, coords, wire)?.let { return "theme word $word: $it" }
        }
        return null
    }

    private fun pathProblem(word: String, coords: List<List<Int>>, wire: CompleteWire): String? {
        if (coords.size != word.length) return "${coords.size} coords for ${word.length} letters"
        if (coords.any { it.size != 2 || it[0] !in 0 until wire.rows || it[1] !in 0 until wire.cols }) return "coord off the board"
        val cells = coords.map { (row, col) -> Coord(row, col) }
        val spelled = cells.joinToString("") { wire.board[it.row][it.col].toString() }
        if (spelled != word.uppercase()) return "coords spell $spelled"
        if (!StrandsPath.isValid(cells, wire.rows, wire.cols)) return "not a traceable path"
        return null
    }
}

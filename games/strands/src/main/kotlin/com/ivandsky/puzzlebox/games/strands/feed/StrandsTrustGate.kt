package com.ivandsky.puzzlebox.games.strands.feed

import java.time.LocalDate

/**
 * Decides whether a parsed day may be played: null when trusted, else the reason it is not.
 * Every field the puzzle is built from is present, the board is rectangular with one letter
 * per cell, the print date is the requested day, and every theme word and the spangram has
 * one in-board `[row, col]` pair per letter — so building the puzzle from a trusted wire
 * cannot fail.
 */
object StrandsTrustGate {

    fun check(wire: StrandsWire, date: LocalDate): String? {
        val printDate = wire.printDate ?: return "missing printDate"
        wire.clue ?: return "missing clue"
        wire.constructors ?: return "missing constructors"
        val spangram = wire.spangram ?: return "missing spangram"
        val themeWords = wire.themeWords ?: return "missing themeWords"
        val board = wire.startingBoard ?: return "missing startingBoard"
        wire.solutions ?: return "missing solutions"
        val themeCoords = wire.themeCoords ?: return "missing themeCoords"
        val spangramCoords = wire.spangramCoords ?: return "missing spangramCoords"

        if (printDate != date.toString()) return "printDate $printDate is not $date"
        val cols = board.firstOrNull()?.length ?: return "empty board"
        if (cols == 0 || board.any { it.length != cols }) return "board is not rectangular"
        if (board.any { row -> row.any { !it.isLetter() } }) return "board cell is not a letter"

        wordCoordsProblem(spangram, spangramCoords, board.size, cols)?.let { return "spangram: $it" }
        themeWords.forEach { word ->
            val coords = themeCoords[word] ?: return "theme word $word has no coords"
            wordCoordsProblem(word, coords, board.size, cols)?.let { return "theme word $word: $it" }
        }
        return null
    }

    private fun wordCoordsProblem(word: String, coords: List<List<Int>>, rows: Int, cols: Int): String? {
        if (coords.size != word.length) return "${coords.size} coords for ${word.length} letters"
        if (coords.any { it.size != 2 || it[0] !in 0 until rows || it[1] !in 0 until cols }) return "coord off the board"
        return null
    }
}

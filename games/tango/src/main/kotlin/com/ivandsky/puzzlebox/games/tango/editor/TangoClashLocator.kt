package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoCountRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoMarkerRule.violates
import com.ivandsky.puzzlebox.games.tango.engine.TangoTripleRule

/**
 * Cells of rule breaks among a puzzle's givens: marker edges whose two given ends disagree,
 * runs of three identical givens, and lines holding more than half of one symbol. Each rule
 * class judges a board holding only the candidate cells, so the answer is every cell involved,
 * not just the first one a rule reports.
 */
object TangoClashLocator {

    fun conflicts(puzzle: TangoPuzzle): Set<Int> = markerCells(puzzle) + tripleCells(puzzle) + countCells(puzzle)

    private fun markerCells(puzzle: TangoPuzzle): Set<Int> =
        puzzle.markers.flatMap { marker ->
            val other = if (marker.side == TangoEdgeSide.RIGHT) marker.cell + 1 else marker.cell + puzzle.n
            if (marker.kind.violates(puzzle.givens[marker.cell], puzzle.givens[other])) listOf(marker.cell, other) else emptyList()
        }.toSet()

    private fun tripleCells(puzzle: TangoPuzzle): Set<Int> =
        lines(puzzle.n).flatMap { line ->
            line.windowed(3).filter { window ->
                TangoTripleRule.violation(puzzle, boardOf(puzzle, window)) != null
            }.flatten()
        }.toSet()

    private fun countCells(puzzle: TangoPuzzle): Set<Int> =
        lines(puzzle.n).flatMap { line ->
            val offender = TangoCountRule.violation(puzzle, boardOf(puzzle, line))?.let { puzzle.givens[it] }
            if (offender == null) emptyList() else line.filter { puzzle.givens[it] == offender }
        }.toSet()

    /** A board holding only the givens among [cells]. */
    private fun boardOf(puzzle: TangoPuzzle, cells: List<Int>): List<TangoCell?> =
        List(puzzle.cellCount) { idx -> if (idx in cells) puzzle.givens[idx] else null }

    /** Every row, then every column, as row-major cell indices. */
    private fun lines(n: Int): List<List<Int>> =
        (0 until n).map { r -> (0 until n).map { c -> r * n + c } } +
            (0 until n).map { c -> (0 until n).map { r -> r * n + c } }
}

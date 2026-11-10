package com.ivandsky.puzzlebox.games.sudoku.engine.hints.step

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuCandidates
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHints
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import kotlin.math.abs

/**
 * The one next step on a board, in catalog order: the wrong entry nearest the last move, then the
 * cheapest single (last cell, box, line, naked), then one elimination, then a reveal. Candidates
 * come from the givens and correct entries minus the `known` strikes (cell to digit mask), so notes
 * and wrong entries never seed a step. Mistakes and the reveal digit read the stored solution;
 * nothing is solved here.
 */
object SudokuStepper {

    // The legacy ladder names the single rung, so the stepper and SudokuHints.hint never disagree on it.
    private val ladder = SudokuHints(emptyList())

    fun next(puzzle: SudokuPuzzle, board: SudokuBoard, lastMove: Int?, known: Map<Int, Int>): SudokuStep {
        mistake(puzzle, board, lastMove)?.let { return it }
        if (board.isFull) return SudokuStep.Solved

        val geometry = puzzle.geometry
        val candidates = SudokuCandidates.of(puzzle, board)
        for ((cell, mask) in known) candidates[cell] = candidates[cell] and mask.inv()

        val scan = Scan(geometry, board, candidates, lastMove)
        scan.cheapest(scan.lastCells())?.let { return it }
        val rung = ladder.single(geometry, candidates, null, emptyList())?.technique
            ?: return SudokuEliminations.next(geometry, candidates, lastMove) { ladder.single(geometry, it, null, emptyList()) != null }
                ?: reveal(puzzle, board, candidates, lastMove)
        val found = if (rung == SudokuTechnique.NAKED_SINGLE) scan.nakedSingles() else scan.hiddenSingles(rung)
        return checkNotNull(scan.cheapest(found)) { "the ladder found a $rung the scan missed" }
    }

    private fun mistake(puzzle: SudokuPuzzle, board: SudokuBoard, lastMove: Int?): SudokuStep.Mistake? {
        val geometry = puzzle.geometry
        // Ascending scan, and minBy keeps the first minimum: the lowest index wins a distance tie.
        val cell = board.cells.indices
            .filter { idx ->
                val entry = board.cells[idx]
                entry is SudokuCell.Entry && entry.digit != puzzle.solution[idx]
            }
            .minByOrNull { geometry.distance(it, lastMove) }
            ?: return null
        val digit = board.digitAt(cell)
        return SudokuStep.Mistake(cell, digit, geometry.peersOf(cell).firstOrNull { board.digitAt(it) == digit })
    }

    private fun reveal(puzzle: SudokuPuzzle, board: SudokuBoard, candidates: IntArray, lastMove: Int?): SudokuStep.Reveal {
        val geometry = puzzle.geometry
        val cell = candidates.indices
            .filter { board.digitAt(it) == 0 }
            .minWith(compareBy({ Integer.bitCount(candidates[it]) }, { geometry.distance(it, lastMove) }))
        return SudokuStep.Reveal(cell, puzzle.solution[cell])
    }

    /** One rung's singles over a mistake-free [board]; every list is in units order, then ascending digit or cell. */
    private class Scan(
        private val geometry: SudokuGeometry,
        private val board: SudokuBoard,
        private val candidates: IntArray,
        private val lastMove: Int?,
    ) {

        /** [cost] is what the step shows to justify itself: ringed blockers plus strike-only excluded marks. */
        class Ranked(val single: SudokuStep.Single, val cost: Int)

        private val order = compareBy<Ranked>(
            { it.cost },
            { geometry.distance(it.single.place.cell, lastMove) },
            { it.single.place.cell },
        )

        fun cheapest(found: List<Ranked>): SudokuStep.Single? = found.minWithOrNull(order)?.single

        /** D0: a unit with one empty cell. Its candidates are the one digit the unit lacks, as it sees the rest. */
        fun lastCells(): List<Ranked> = geometry.units.mapNotNull { unit ->
            val cell = geometry.cellsOf(unit).singleOrNull { board.digitAt(it) == 0 } ?: return@mapNotNull null
            val mask = candidates[cell]
            if (Integer.bitCount(mask) != 1) return@mapNotNull null
            Ranked(SudokuStep.Single(place(cell, digitOf(mask), hiddenTechnique(unit), unit), unit, emptySet()), 0)
        }

        /**
         * D1 or D2: a digit with one home in a box, or in a row or column. An other open cell no placed digit
         * explains lost the digit to a known strike; it needs no blocker but costs one excluded mark.
         */
        fun hiddenSingles(technique: SudokuTechnique): List<Ranked> {
            val inBox = technique == SudokuTechnique.HIDDEN_SINGLE_BOX
            val found = mutableListOf<Ranked>()
            for (unit in geometry.units) {
                if ((unit.kind == UnitKind.BOX) != inBox) continue
                val cells = geometry.cellsOf(unit)
                for (digit in 1..geometry.size) {
                    val bit = 1 shl (digit - 1)
                    val target = cells.singleOrNull { candidates[it] and bit != 0 } ?: continue
                    val others = cells.filter { it != target && board.digitAt(it) == 0 }
                    val holders = candidates.indices.filter { board.digitAt(it) == digit }
                    val explained = others.filter { cell -> holders.any { sees(it, cell) } }
                    val blockers = blockers(explained, holders)
                    val struck = (others - explained.toSet()).associateWith { bit }
                    val single = SudokuStep.Single(place(target, digit, technique, unit), null, blockers, struck)
                    found += Ranked(single, blockers.size + struck.size)
                }
            }
            return found
        }

        /**
         * D3: a cell with one candidate; per digit it sees, the nearest peer holding it, lowest index on a tie.
         * Every other digit is either a blocker or a strike mark, so all naked singles cost the same; the strike
         * marks are the digits no peer holds, all on the cell itself.
         */
        fun nakedSingles(): List<Ranked> = candidates.indices
            .filter { Integer.bitCount(candidates[it]) == 1 }
            .map { cell ->
                val digit = digitOf(candidates[cell])
                val peers = geometry.peersOf(cell)
                val blockers = LinkedHashSet<Int>()
                var struckMask = 0
                for (seen in 1..geometry.size) {
                    if (seen == digit) continue
                    val nearest = peers.filter { board.digitAt(it) == seen }.minByOrNull { geometry.distance(it, cell) }
                    if (nearest != null) blockers += nearest else struckMask = struckMask or (1 shl (seen - 1))
                }
                val struck = if (struckMask == 0) emptyMap() else mapOf(cell to struckMask)
                Ranked(SudokuStep.Single(place(cell, digit, SudokuTechnique.NAKED_SINGLE, null), null, blockers, struck), geometry.size - 1)
            }

        /**
         * The [holders] that rule their digit out of [explained], picked greedily: each pick explains the most
         * cells still unexplained, nearest the last move, lowest index.
         */
        private fun blockers(explained: List<Int>, holders: List<Int>): Set<Int> {
            var unexplained = explained
            val chosen = LinkedHashSet<Int>()
            while (unexplained.isNotEmpty()) {
                val pick = holders.minWith(
                    compareBy({ holder -> -unexplained.count { sees(holder, it) } }, { geometry.distance(it, lastMove) }),
                )
                chosen += pick
                unexplained = unexplained.filterNot { sees(pick, it) }
            }
            return chosen
        }

        private fun sees(a: Int, b: Int): Boolean =
            geometry.rowOf(a) == geometry.rowOf(b) || geometry.colOf(a) == geometry.colOf(b) || geometry.boxOf(a) == geometry.boxOf(b)

        private fun place(cell: Int, digit: Int, technique: SudokuTechnique, unit: SudokuUnit?) =
            SudokuHint.Place(cell, digit, technique, unit, null, emptyList())

        private fun hiddenTechnique(unit: SudokuUnit): SudokuTechnique =
            if (unit.kind == UnitKind.BOX) SudokuTechnique.HIDDEN_SINGLE_BOX else SudokuTechnique.HIDDEN_SINGLE_LINE

        private fun digitOf(mask: Int): Int = Integer.numberOfTrailingZeros(mask) + 1
    }
}

/** Chebyshev distance between two cells; 0 with no last move, so ties fall to the lowest index. */
internal fun SudokuGeometry.distance(cell: Int, lastMove: Int?): Int =
    if (lastMove == null) 0 else maxOf(abs(rowOf(cell) - rowOf(lastMove)), abs(colOf(cell) - colOf(lastMove)))

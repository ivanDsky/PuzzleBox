package com.ivandsky.puzzlebox.games.sudoku.engine.hints.step

import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique

/**
 * The one elimination to show when no single exists (catalog D4 to D7): the cheapest rung with an
 * elimination after which a single appears at once, else the cheapest rung with any elimination.
 * Inside a rung the elimination striking nearest the last move wins, then scan order.
 *
 * The rungs enumerate every elimination themselves instead of running `EliminationStep`s, which
 * clear the first one they find in place and so cannot offer the alternatives tier 1 compares.
 * Triples are left out: on a six-cell unit they were never the cheapest step (catalog).
 */
internal object SudokuEliminations {

    private val rungs: List<(SudokuGeometry, IntArray) -> List<SudokuStep.Elimination>> = listOf(
        { g, c -> intersections(g, c, pointing = true) },
        { g, c -> intersections(g, c, pointing = false) },
        ::nakedPairs,
        ::hiddenPairs,
    )

    /**
     * [candidates] already carry the known strikes, so an elimination the player was shown strikes
     * nothing and is never offered again. [unlocks] tells whether candidates hold a single.
     */
    fun next(
        geometry: SudokuGeometry,
        candidates: IntArray,
        lastMove: Int?,
        unlocks: (IntArray) -> Boolean,
    ): SudokuStep.Elimination? {
        val nearest = compareBy<SudokuStep.Elimination> { step -> step.struck.keys.minOf { geometry.distance(it, lastMove) } }
        var fallback: SudokuStep.Elimination? = null
        for (rung in rungs) {
            val found = rung(geometry, candidates)
            if (found.isEmpty()) continue
            found.filter { unlocks(after(candidates, it.struck)) }.minWithOrNull(nearest)?.let { return it }
            if (fallback == null) fallback = found.minWith(nearest)
        }
        return fallback
    }

    private fun after(candidates: IntArray, struck: Map<Int, Int>): IntArray =
        candidates.copyOf().also { next -> for ((cell, mask) in struck) next[cell] = next[cell] and mask.inv() }

    /**
     * Pointing: a box whose digit sits on one line strikes it from the rest of that line; the box is the unit.
     * Claiming: a line whose digit sits in one box strikes it from the rest of that box; the line is the unit.
     * The evidence is the cells of the unit that still hold the digit.
     */
    private fun intersections(geometry: SudokuGeometry, candidates: IntArray, pointing: Boolean): List<SudokuStep.Elimination> {
        val technique = if (pointing) SudokuTechnique.POINTING else SudokuTechnique.CLAIMING
        val found = mutableListOf<SudokuStep.Elimination>()
        for (unit in geometry.units) {
            if ((unit.kind == UnitKind.BOX) != pointing) continue
            val cells = geometry.cellsOf(unit)
            for (digit in 1..geometry.size) {
                val bit = 1 shl (digit - 1)
                val holders = cells.filter { candidates[it] and bit != 0 }
                // One holder is a hidden single, which the stepper has already ruled out.
                if (holders.size < 2) continue
                val other = confining(geometry, holders, pointing) ?: continue
                val inUnit = cells.toSet()
                val struck = strike(candidates, geometry.cellsOf(other).filter { it !in inUnit }, bit)
                if (struck.isNotEmpty()) found += SudokuStep.Elimination(technique, unit, bit, struck, holders.toSet())
            }
        }
        return found
    }

    /** The other unit holding every one of [holders]: their line for pointing, their box for claiming. */
    private fun confining(geometry: SudokuGeometry, holders: List<Int>, pointing: Boolean): SudokuUnit? {
        val first = holders.first()
        return when {
            !pointing -> geometry.boxOf(first).takeIf { box -> holders.all { geometry.boxOf(it) == box } }
                ?.let { SudokuUnit(UnitKind.BOX, it) }
            holders.all { geometry.rowOf(it) == geometry.rowOf(first) } -> SudokuUnit(UnitKind.ROW, geometry.rowOf(first))
            holders.all { geometry.colOf(it) == geometry.colOf(first) } -> SudokuUnit(UnitKind.COLUMN, geometry.colOf(first))
            else -> null
        }
    }

    /** Two open cells of a unit holding only the same two digits strike both from the unit's other open cells. */
    private fun nakedPairs(geometry: SudokuGeometry, candidates: IntArray): List<SudokuStep.Elimination> {
        val found = mutableListOf<SudokuStep.Elimination>()
        for (unit in geometry.units) {
            val open = geometry.cellsOf(unit).filter { candidates[it] != 0 }
            if (open.size <= 2) continue
            for (i in open.indices) for (j in i + 1 until open.size) {
                val digits = candidates[open[i]] or candidates[open[j]]
                if (Integer.bitCount(digits) != 2) continue
                val struck = strike(candidates, open.filter { it != open[i] && it != open[j] }, digits)
                if (struck.isNotEmpty()) {
                    found += SudokuStep.Elimination(SudokuTechnique.NAKED_PAIR, unit, digits, struck, setOf(open[i], open[j]))
                }
            }
        }
        return found
    }

    /** Two digits whose only homes in a unit are the same two cells strike every other digit from those cells. */
    private fun hiddenPairs(geometry: SudokuGeometry, candidates: IntArray): List<SudokuStep.Elimination> {
        val found = mutableListOf<SudokuStep.Elimination>()
        for (unit in geometry.units) {
            val cells = geometry.cellsOf(unit)
            if (cells.count { candidates[it] != 0 } <= 2) continue
            val homes = (1..geometry.size).map { digit -> cells.filter { candidates[it] and (1 shl (digit - 1)) != 0 } }
            for (a in homes.indices) for (b in a + 1 until homes.size) {
                if (homes[a].isEmpty() || homes[b].isEmpty()) continue
                val pair = (homes[a] + homes[b]).distinct().sorted()
                if (pair.size != 2) continue
                val keep = (1 shl a) or (1 shl b)
                val struck = strike(candidates, pair, keep.inv())
                if (struck.isNotEmpty()) found += SudokuStep.Elimination(SudokuTechnique.HIDDEN_PAIR, unit, keep, struck, pair.toSet())
            }
        }
        return found
    }

    /** Cell to the bits of [mask] each of [cells] still holds, in cell order; cells holding none are left out. */
    private fun strike(candidates: IntArray, cells: List<Int>, mask: Int): Map<Int, Int> =
        cells.mapNotNull { cell -> (candidates[cell] and mask).takeIf { it != 0 }?.let { cell to it } }.toMap()
}

package com.ivandsky.puzzlebox.games.sudoku.engine.hints

import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.UnitKind

/**
 * Box/line intersections. Each step clears one digit from one intersection per call, so the
 * hint can name a single box, line and digit.
 */
internal object IntersectionSteps {

    /** A digit whose box candidates all sit on one line is cleared from the rest of that line. */
    val POINTING: EliminationStep = Intersection(SudokuTechnique.POINTING)

    /** A digit whose line candidates all sit in one box is cleared from the rest of that box. */
    val CLAIMING: EliminationStep = Intersection(SudokuTechnique.CLAIMING)

    private class Intersection(override val technique: SudokuTechnique) : EliminationStep {

        private val pointing = technique == SudokuTechnique.POINTING

        override fun eliminate(geometry: SudokuGeometry, candidates: IntArray): Boolean {
            val boxes = geometry.boxCells()
            for (box in boxes.indices) {
                val corner = boxes[box][0]
                val top = geometry.rowOf(corner)
                val left = geometry.colOf(corner)
                for (row in top until top + geometry.boxRows) {
                    if (clear(geometry, candidates, box, UnitKind.ROW, row)) return true
                }
                for (col in left until left + geometry.boxCols) {
                    if (clear(geometry, candidates, box, UnitKind.COLUMN, col)) return true
                }
            }
            return false
        }

        private fun clear(geometry: SudokuGeometry, candidates: IntArray, box: Int, kind: UnitKind, line: Int): Boolean {
            val boxCells = geometry.boxCells()[box]
            val lineCells = geometry.cellsOf(geometry.units[kind.ordinal * geometry.size + line])
            var shared = 0
            var lineRest = 0
            for (cell in lineCells) {
                if (geometry.boxOf(cell) == box) shared = shared or candidates[cell] else lineRest = lineRest or candidates[cell]
            }
            var boxRest = 0
            for (cell in boxCells) {
                if (lineOf(geometry, kind, cell) != line) boxRest = boxRest or candidates[cell]
            }
            val digits = if (pointing) shared and boxRest.inv() and lineRest else shared and lineRest.inv() and boxRest
            if (digits == 0) return false
            val keep = Integer.lowestOneBit(digits).inv()
            if (pointing) {
                for (cell in lineCells) if (geometry.boxOf(cell) != box) candidates[cell] = candidates[cell] and keep
            } else {
                for (cell in boxCells) if (lineOf(geometry, kind, cell) != line) candidates[cell] = candidates[cell] and keep
            }
            return true
        }

        private fun lineOf(geometry: SudokuGeometry, kind: UnitKind, cell: Int): Int =
            if (kind == UnitKind.ROW) geometry.rowOf(cell) else geometry.colOf(cell)
    }
}

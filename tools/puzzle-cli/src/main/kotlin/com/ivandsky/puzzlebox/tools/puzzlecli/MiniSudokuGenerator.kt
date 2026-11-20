package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver
import kotlin.random.Random

/**
 * Seeded random Mini Sudoku boards: a random full 6×6 grid by backtracking, then the givens removed in random order for
 * as long as the board keeps exactly one solution, so the board is minimal: removing any one more given leaves two
 * solutions. The engine has no generator of its own (the editor's completer fills boards around an author's givens),
 * so this one is the dev tool's.
 */
object MiniSudokuGenerator : DrillGenerator {

    private val variant = SudokuVariants.MINI

    override fun next(random: Random): Puzzle? {
        val solution = fullGrid(variant.geometry, random)
        val givens = minimal(variant.geometry, solution, random)
        val encoded = SudokuCodec.encode(SudokuPuzzle(variant, givens, solution))
        return (SudokuGames.MINI.decode(encoded) as? DecodeResult.Ok)?.value
    }

    /** A random valid full grid: cells in order, each cell's digits tried in random order. */
    private fun fullGrid(geometry: SudokuGeometry, random: Random): List<Int> {
        val cells = IntArray(geometry.cellCount)
        fun fill(idx: Int): Boolean {
            if (idx == cells.size) return true
            // An unfilled peer holds 0, which sets a bit no digit uses.
            val used = geometry.peersOf(idx).fold(0) { mask, peer -> mask or (1 shl cells[peer]) }
            for (digit in (1..geometry.size).shuffled(random)) {
                if (used and (1 shl digit) != 0) continue
                cells[idx] = digit
                if (fill(idx + 1)) return true
            }
            cells[idx] = 0
            return false
        }
        check(fill(0)) { "no ${geometry.size}×${geometry.size} Sudoku grid" }
        return cells.toList()
    }

    /** [solution] with every given dropped in shuffled order that leaves the board uniquely solvable. */
    private fun minimal(geometry: SudokuGeometry, solution: List<Int>, random: Random): List<Int> {
        val givens = solution.toMutableList()
        for (idx in solution.indices.shuffled(random)) {
            val digit = givens[idx]
            givens[idx] = 0
            if (SudokuSolver.solve(geometry, givens, limit = 2).count != 1) givens[idx] = digit
        }
        return givens
    }
}

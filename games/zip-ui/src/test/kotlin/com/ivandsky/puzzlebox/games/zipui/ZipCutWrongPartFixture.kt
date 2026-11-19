package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle

/**
 * The two boards of backlog #1585, each with its one solution (found by exhaustive search, no walls):
 * daily #527 (7x7) with a detour off the solution after 40 cells, and phone daily #3 (6x6) with a
 * line that is right so far. Cells are `row * n + col`.
 */
internal object ZipCutWrongPartFixture {

    private fun puzzle(n: Int, waypoints: List<Int>, solution: List<Int>, stored: Boolean = true) = ZipPuzzle(
        variant = VariantId("zip/n$n"),
        board = ZipBoard(n = n, walls = emptySet(), waypoints = waypoints.map(::Cell)),
        solution = if (stored) solution.map(::Cell) else null,
    )

    private val solution527 = listOf(
        4, 11, 18, 19, 12, 5, 6, 13, 20, 27, 34, 41, 48, 47, 46, 45, 38, 39, 40, 33, 26, 25, 32, 31, 24,
        17, 16, 23, 30, 37, 44, 43, 42, 35, 36, 29, 28, 21, 14, 7, 0, 1, 2, 3, 10, 9, 8, 15, 22,
    )

    private val waypoints527 = listOf(4, 19, 5, 45, 33, 26, 31, 24, 17, 44, 43, 29, 3, 15, 22)

    fun daily527(stored: Boolean = true): ZipPuzzle = puzzle(7, waypoints527, solution527, stored)

    /** Index of the last cell, (0,1) at column 0 row 1, on #527's solution before the detour leaves it. */
    const val LAST_RIGHT_527 = 39

    /** The solution's first 40 cells, then off it to (1,1), (1,0), (2,0), (3,0), (3,1) and (2,1): the tail (2,1) sits beside (1,1). */
    val detour527: List<Int> = solution527.take(LAST_RIGHT_527 + 1) + listOf(8, 1, 2, 3, 10, 9)

    val solution527Cells: List<Int> get() = solution527

    private val solutionPhone3 = listOf(
        21, 22, 28, 27, 26, 25, 19, 13, 7, 8, 9, 3, 2, 1, 0, 6, 12, 18, 24, 30, 31, 32, 33, 34, 35,
        29, 23, 17, 11, 5, 4, 10, 16, 15, 14, 20,
    )

    fun phone3(): ZipPuzzle = puzzle(6, listOf(21, 28, 25, 3, 24, 29, 4, 20), solutionPhone3)

    /** The first 14 cells of the phone board's solution, ending at (1,0), above (1,1) that the line passed 5 cells back. */
    val right3: List<Int> = solutionPhone3.take(14)
}

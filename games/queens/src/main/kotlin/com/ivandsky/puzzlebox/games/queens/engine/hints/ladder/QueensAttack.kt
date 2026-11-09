package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import kotlin.math.abs

/**
 * The ladder's attack relation on cell indices. It reads board geometry only: on an N=2
 * board a shared unit rules a cell out only once that unit holds its queens, which is the
 * caller's call.
 */
internal object QueensAttack {

    /** [a] and [b] are distinct and at Chebyshev distance 1 on a [size]-wide board. */
    fun touches(a: Int, b: Int, size: Int): Boolean =
        a != b && abs(a / size - b / size) <= 1 && abs(a % size - b % size) <= 1

    /** [a] and [b] are distinct and in the same row, column or region. */
    fun sharesUnit(puzzle: QueensPuzzle, a: Int, b: Int): Boolean {
        val n = puzzle.size
        return a != b && (a / n == b / n || a % n == b % n || puzzle.regions[a] == puzzle.regions[b])
    }

    fun attacks(puzzle: QueensPuzzle, a: Int, b: Int): Boolean =
        touches(a, b, puzzle.size) || sharesUnit(puzzle, a, b)
}

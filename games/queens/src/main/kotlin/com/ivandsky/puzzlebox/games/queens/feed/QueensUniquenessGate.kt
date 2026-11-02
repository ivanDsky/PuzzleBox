package com.ivandsky.puzzlebox.games.queens.feed

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver

/**
 * The A13+A5 decode-time uniqueness gate (queens-06): the official daily ships its
 * `solution` in the wire, so the client can prove the board is well-posed BEFORE the
 * puzzle ever becomes playable. The gate independently solves the colorGrid and requires
 * the result to equal the shipped solution:
 *
 * 1. a solution must be shipped at all (no oracle, no trust);
 * 2. the shipped solution must satisfy the queens-02 rule pipeline
 *    ([QueensRules.evaluate] is Valid);
 * 3. the independent exhaustive solve ([QueensSolver], limit 2) must find EXACTLY one
 *    solution (ambiguous boards are rejected outright — A5);
 * 4. that unique solution must equal the shipped coordinates.
 *
 * Any failure is a typed [DecodeError]; the puzzle never becomes playable and there is
 * NO silent fallback. The caller maps [DecodeError] to `FetchError.Untrusted` at the
 * fetch boundary (zip LinkedInZipDailySource precedent). The `solution` itself never
 * enters play state — hints consume it only via the hint engine (A13).
 */
object QueensUniquenessGate {

    /** Null when the board is proven unique and the shipped solution is correct. */
    fun check(puzzle: QueensPuzzle): DecodeError? {
        val shipped = puzzle.solution
            ?: return DecodeError.Malformed("daily ships no solution; A13 gate cannot validate")
        when (val verdict = QueensRules.evaluate(puzzle, shipped.toSet())) {
            is QueensRulesVerdict.Valid -> Unit
            is QueensRulesVerdict.Incomplete ->
                return DecodeError.Malformed(
                    "shipped solution is incomplete: ${verdict.placed}/${verdict.required} queens",
                )
            is QueensRulesVerdict.Invalid ->
                return DecodeError.Malformed(
                    "shipped solution violates queens rules (${verdict.violations.size} violations)",
                )
        }
        val solver = QueensSolver()
        val count = solver.countSolutions(puzzle, limit = 2)
        if (count != 1) {
            return DecodeError.Malformed("independent solve found $count solutions, need exactly 1 (ambiguous board)")
        }
        val found = solver.solve(puzzle)
        if (found != shipped) {
            return DecodeError.Malformed("independent solve disagrees with shipped solution")
        }
        return null
    }
}

package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle

/**
 * The first-move rule for a Zip hint (nav-rethink B4): a hint never points at a step the player
 * already knows. A step is known when the trace has one legal way on, which covers the start cell
 * of an empty board. Every other step is a real choice.
 *
 * [pick] walks a solution from the trace head, passes the known steps and returns the first real
 * choice that [forced] proves is the only one that still finishes the board. The passed cells come
 * back as [ZipHint.Step.lead]. When no choice is proven the hint is the first real choice; when no
 * real choice is left it is the next cell, as before, except that an empty board still gets its
 * start cell in the lead. Pure: the proof is the only search, and the caller supplies it.
 *
 * @param forced true when none of `others`, the other legal cells after `prefix`, can finish the board.
 */
class ZipFirstMovePicker(
    private val forced: (puzzle: ZipPuzzle, prefix: List<Cell>, others: List<Cell>) -> Boolean,
) {

    /** [line] is a full solution that starts with [path] and is longer than it. */
    fun pick(puzzle: ZipPuzzle, path: List<Cell>, line: List<Cell>): ZipHint.Step {
        var firstChoice = -1
        for (at in path.size until line.size) {
            val prefix = line.subList(0, at)
            val others = otherWays(puzzle, prefix, line[at])
            if (others.isEmpty()) continue
            if (forced(puzzle, prefix, others)) return step(path, line, at)
            if (firstChoice < 0) firstChoice = at
        }
        val fallback = when {
            firstChoice >= 0 -> firstChoice
            path.isEmpty() && line.size > 1 -> 1
            else -> path.size
        }
        return step(path, line, fallback)
    }

    private fun step(path: List<Cell>, line: List<Cell>, at: Int) = ZipHint.Step(line[at], line.subList(path.size, at))

    /** The legal cells after [prefix] other than [step]; none on an empty trace, which may only start at 1. */
    private fun otherWays(puzzle: ZipPuzzle, prefix: List<Cell>, step: Cell): List<Cell> {
        val head = prefix.lastOrNull() ?: return emptyList()
        val state = ZipState(puzzle, prefix)
        return puzzle.board.neighbors(head).filter {
            it != step && ZipRules.validateMove(ZipMove.Extend(it), state) is ZipValidation.Valid
        }
    }
}

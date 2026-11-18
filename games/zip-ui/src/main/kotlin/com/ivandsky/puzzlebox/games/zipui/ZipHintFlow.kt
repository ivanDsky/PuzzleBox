package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.engine.ZipHint
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation

/**
 * One press of Hint, as moves on the log (A3, F2 §10, ticket 28). A provably dead trace is trimmed
 * free, to the longest prefix that still solves. Then the one counted hint places one cell.
 *
 * That cell is the first real choice: the cells before it, which have one way on,
 * are placed first as plain moves, so the hint stays one count and one hinted cell in the replay
 * (ADR-0025). The record is trim, plain moves, Hint, the hinted cell, all at one stamp.
 */
internal class ZipHintFlow(
    private val rules: ZipRules,
    private val log: ZipMoveLog,
) {

    /**
     * What one press did: the [session] to store, the prefix [kept] when a dead trace was trimmed,
     * the [cell] the hint points at, and [placed] with its [arrow] when that cell went onto the trace.
     */
    class Outcome(
        val session: ZipSession,
        val kept: List<Cell>?,
        val cell: Int?,
        val placed: Int?,
        val arrow: HintArrow?,
    )

    fun press(current: ZipSession): Outcome {
        val puzzle = current.puzzle
        var next = current
        var kept: List<Cell>? = null
        var resolved = resolve(ZipState(puzzle, next.path))
        if (resolved is ZipHint.Backtrack) {
            next = log.record(
                next,
                if (resolved.trimTo < 0) ZipReplayMove.Reset else ZipReplayMove.RetractTo(next.path[resolved.trimTo]),
            )
            kept = next.path
            resolved = resolve(ZipState(puzzle, next.path))
        }
        val step = resolved as? ZipHint.Step
        // Before the count: these are the player's own obvious moves, not the hint's.
        for (cell in step?.lead.orEmpty()) {
            if (!legal(next, cell)) break
            next = log.record(next, ZipReplayMove.Extend(cell))
        }
        // Counted before the step: a hint that places the last cell wins, and a won session takes no count.
        next = log.record(next, ZipReplayMove.Hint)
        var placed: Int? = null
        var arrow: HintArrow? = null
        if (step != null && legal(next, step.cell)) {
            next = log.record(next, ZipReplayMove.Extend(step.cell))
            placed = step.cell.idx
            // A lone start cell has no segment direction, so no arrow.
            arrow = next.path.getOrNull(next.path.size - 2)?.let { tail -> HintArrow(headIdx = step.cell.idx, tailIdx = tail.idx) }
        }
        return Outcome(next, kept, step?.cell?.idx, placed, arrow)
    }

    private fun resolve(state: ZipState): ZipHint = rules.firstMoveHint(state)

    private fun legal(session: ZipSession, cell: Cell): Boolean =
        rules.validateMove(ZipMove.Extend(cell), ZipState(session.puzzle, session.path)) is ZipValidation.Valid
}

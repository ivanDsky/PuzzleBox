package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict

/** The drawable per-cell state (direction doc §3). */
enum class QueensCellVisual { EMPTY, X_MANUAL, X_AUTO, QUEEN }

/**
 * The renderer-model mapping — the queens-ui-side drawable model derived from the
 * session. Pure function, no Compose in :games:queens (ADR-0001):
 * the session carries provenance-aware marks, this model flattens them into paint
 * decisions.
 *
 * Violations are ALWAYS ON (A14 revised, fix round 6 — the Auto-check toggle is
 * deleted): the rules pipeline's attributed offending cells (typed verdict from
 * queens-02) render stripes + caption whenever a conflict is live. The one exception is
 * a guided lesson (spec A12): it teaches input before any rule, so the guide passed to
 * [of] holds the stripes back and instead shows [ghosts] — faint crowns on the answer
 * squares that still lack a queen — and [strays], the queens off the answer.
 */
data class QueensBoardModel(
    val n: Int,
    val regions: List<Int>,
    val visuals: List<QueensCellVisual>, // row-major, n*n entries
    val queens: List<Int>, // row-major indices, PLACEMENT order (win cascade §5.6)
    val violations: Set<Int>, // row-major indices under the live conflict highlighting
    val win: Boolean,
    val ghosts: Set<Int> = emptySet(), // guided lesson: answer squares still without a queen
    val strays: Set<Int> = emptySet(), // guided lesson: queens on squares outside the answer
) {
    val cellCount: Int get() = n * n

    companion object {

        /** session, guided answer → drawable model (the RendererModel contract shape). A non-null [guide] is the answer's row-major indices. */
        fun of(session: QueensSession, guide: Set<Int>? = null): QueensBoardModel {
            val puzzle = session.puzzle
            val queenSet = session.queens.toSet()
            val visuals = List(puzzle.cellCount) { idx ->
                when {
                    Cell(idx) in queenSet -> QueensCellVisual.QUEEN
                    session.markAt(Cell(idx))?.kind == MarkKind.MANUAL -> QueensCellVisual.X_MANUAL
                    session.markAt(Cell(idx))?.kind == MarkKind.AUTO -> QueensCellVisual.X_AUTO
                    else -> QueensCellVisual.EMPTY
                }
            }
            val violations = if (guide == null && !session.isComplete) {
                when (val verdict = QueensRules.evaluate(puzzle, queenSet)) {
                    is QueensRulesVerdict.Invalid -> verdict.violations.map { it.cell.idx }.toSet()
                    else -> emptySet()
                }
            } else {
                emptySet()
            }
            return QueensBoardModel(
                n = puzzle.size,
                regions = puzzle.regions,
                visuals = visuals,
                queens = session.queens.map { it.idx },
                violations = violations,
                win = session.isComplete,
                ghosts = guide.orEmpty() - queenSet.map { it.idx }.toSet(),
                strays = guide?.let { answer -> queenSet.map { it.idx }.toSet() - answer }.orEmpty(),
            )
        }
    }
}

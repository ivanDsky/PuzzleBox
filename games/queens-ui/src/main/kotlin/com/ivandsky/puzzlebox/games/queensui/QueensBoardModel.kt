package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict

/** The drawable per-cell state (direction doc §3). */
enum class QueensCellVisual { EMPTY, X_MANUAL, X_AUTO, QUEEN }

/**
 * The renderer-model mapping — the queens-ui-side drawable model derived from the
 * session and the hint payload. Pure function, no Compose in :games:queens (ADR-0001):
 * the session carries provenance-aware marks, this model flattens them into paint
 * decisions.
 *
 * Violations are ALWAYS ON (A14 revised, fix round 6 — the Auto-check toggle is
 * deleted): the rules pipeline's attributed offending cells (typed verdict from
 * queens-02) render stripes + caption whenever a conflict is live.
 *
 * [hintCell] is the staged ladder's stage 2 (fix round 6): a repeat hint tap on the
 * same forced cell POINTS at it — a crown ghost renders over the region pulse.
 */
data class QueensBoardModel(
    val n: Int,
    val regions: List<Int>,
    val visuals: List<QueensCellVisual>, // row-major, n*n entries
    val queens: List<Int>, // row-major indices, PLACEMENT order (win cascade §5.6)
    val violations: Set<Int>, // row-major indices under the live conflict highlighting
    val hintRegion: Int?,
    val hintCells: Set<Int>, // region cells (Region hint) or offending cells (Incorrect hint)
    val hintCell: Int?, // stage 2: the pointed forced cell (crown ghost)
    val win: Boolean,
) {
    val cellCount: Int get() = n * n

    companion object {

        /** state, hint, stage-2 flag → drawable model (the RendererModel contract shape). */
        fun of(
            session: QueensSession,
            hint: QueensHint? = null,
            hintPointed: Boolean = false,
        ): QueensBoardModel {
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
            val violations = if (!session.isComplete) {
                when (val verdict = QueensRules.evaluate(puzzle, queenSet)) {
                    is QueensRulesVerdict.Invalid -> verdict.violations.map { it.cell.idx }.toSet()
                    else -> emptySet()
                }
            } else {
                emptySet()
            }
            val (hintRegion, hintCells) = when (hint) {
                is QueensHint.Region ->
                    hint.region to puzzle.regions.indices.filter { puzzle.regions[it] == hint.region }.toSet()
                is QueensHint.Incorrect -> null to hint.cells.map { it.idx }.toSet()
                else -> null to emptySet<Int>()
            }
            val hintCell = (hint as? QueensHint.Region)?.cell?.idx?.takeIf { hintPointed }
            return QueensBoardModel(
                n = puzzle.size,
                regions = puzzle.regions,
                visuals = visuals,
                queens = session.queens.map { it.idx },
                violations = violations,
                hintRegion = hintRegion,
                hintCells = hintCells,
                hintCell = hintCell,
                win = session.isComplete,
            )
        }
    }
}

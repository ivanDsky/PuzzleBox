package com.ivandsky.puzzlebox.games.queens.render

import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensState
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensVerifier

/**
 * Game-authored paint-by-numbers for :core:game-ui's BoardGrid (zip ZipRendererModel
 * shape): board size, the per-cell region id, the placed queen cells, and the play
 * status. :games:queens-ui maps it to Compose; nothing in game-ui or core ever imports
 * this module. Marks/provenance rendering (A10–A12) extends the cell payload when the
 * marks tickets land.
 */
data class QueensRendererModel(
    val n: Int,
    val regions: List<Int>, // row-major, n*n entries
    val queens: Set<Int>, // row-major indices of placed queens
    val hintHighlight: Int?,
    val status: Status,
) : RendererModel {

    enum class Status { IDLE, PLAYING, COMPLETE }

    companion object {

        /**
         * [hint] is the game-typed payload ([QueensHint]): a [QueensHint.Region] hint
         * highlights its forced cell; [QueensHint.Incorrect] carries multiple cells and
         * reduces to no single highlight. Status derives from the placement: complete
         * and rule-clean → COMPLETE, anything placed → PLAYING, else IDLE.
         */
        fun of(puzzle: QueensPuzzle, queens: Set<Int>, hint: Hint? = null): QueensRendererModel =
            QueensRendererModel(
                n = puzzle.size,
                regions = puzzle.regions,
                queens = queens,
                hintHighlight = (hint as? QueensHint.Region)?.cell?.idx,
                status = when {
                    queens.size == puzzle.size * puzzle.queensPerUnit &&
                        QueensVerifier.isSolution(puzzle, queens.map(::Cell).toSet()) -> Status.COMPLETE
                    queens.isNotEmpty() -> Status.PLAYING
                    else -> Status.IDLE
                },
            )
    }
}

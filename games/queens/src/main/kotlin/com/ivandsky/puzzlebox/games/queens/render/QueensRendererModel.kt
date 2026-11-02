package com.ivandsky.puzzlebox.games.queens.render

import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensState

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

        fun of(puzzle: QueensPuzzle, queens: Set<Int>, hint: Hint? = null): QueensRendererModel =
            QueensRendererModel(
                n = puzzle.size,
                regions = puzzle.regions,
                queens = queens,
                // Real hints are queens-02's typed payload; nothing to highlight yet.
                hintHighlight = null,
                status = Status.IDLE, // completion detection arrives with the rules verdict (queens-02)
            )
    }
}

package com.ivandsky.puzzlebox.games.tangoui.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoTrainerMove

/**
 * The state of one snippet board: the cut's grid as drawn and the one move the player makes. The grid is only ever
 * drawn; the verdict is the move check's, not the board's.
 *
 * A tap makes the move that follows the target step (a sun for a step that places a sun, a moon for a step that
 * places a moon) on an empty cell that is no given, once. A given or a filled cell takes no tap.
 */
internal class TangoSnippetBoardState(private val snippet: TangoSnippet) {

    /** The grid as drawn: the cut's own, plus the player's move once made. */
    var grid: List<TangoCell?> by mutableStateOf(snippet.cut.gridBefore)
        private set

    /** The player's move, null until one is made. */
    var played: TangoTrainerMove? = null
        private set

    /**
     * The player's move for a tap on the cell [idx], now drawn; null when no move is made: one was made already,
     * [idx] is off the board, or the cell is a given or already filled.
     */
    fun tap(idx: Int): TangoTrainerMove? {
        if (played != null || idx !in 0 until snippet.puzzle.cellCount) return null
        if (snippet.puzzle.isGiven(idx) || grid[idx] != null) return null
        val move = TangoTrainerMove(idx, snippet.value)
        grid = grid.toMutableList().also { it[idx] = move.value }
        played = move
        return move
    }
}

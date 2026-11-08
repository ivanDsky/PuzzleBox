package com.ivandsky.puzzlebox.profile.board

import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.model.GameId

/**
 * The per-game board (A2): [selected] is the game it shows (null while the default is still
 * being resolved, so [state] is Loading), ranked by [order]; [state] is the signed-in board
 * or SignedOut.
 */
data class PerGameBoard(
    val selected: GameId?,
    val order: RankOrder,
    val state: DailyBoardState,
)

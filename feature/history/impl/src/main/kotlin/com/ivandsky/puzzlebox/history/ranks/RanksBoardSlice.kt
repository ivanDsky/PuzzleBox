package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState

/** What the board slot draws: the kit's [state] under [title] and the rule caption of [order]. */
internal data class RanksBoardSlice(
    val title: String,
    val order: RankOrder,
    val state: DailyBoardState,
)

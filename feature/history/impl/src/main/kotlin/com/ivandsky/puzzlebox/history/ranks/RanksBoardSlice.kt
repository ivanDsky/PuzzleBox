package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState

/**
 * What the board slot draws: the kit's [state] under [title] and the rule caption of [order]
 * (or [caption] when the board ranks by something else, like Overall's points), with [note]
 * set under the rows. [friendsAlone] is set in Friends scope when nobody but the viewer is on the board (a hint says how to fill it).
 */
internal data class RanksBoardSlice(
    val title: String,
    val order: RankOrder,
    val state: DailyBoardState,
    val caption: String? = null,
    val note: String? = null,
    val friendsAlone: Boolean = false,
)

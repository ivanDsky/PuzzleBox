package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState

/**
 * What the board slot draws: the kit's [state] under [title] and the rule caption of [order]
 * (or [caption] when the board ranks by something else, like Overall's points), with [note]
 * set under the rows. [friendsAlone] is set in Friends scope when nobody but the viewer is on the board (a hint says how to fill it).
 * [freshness] is the line under the board saying when its LinkedIn part was last read, null when it has none.
 * [linkedIn] is whether LinkedIn connections can ever join this board (a LinkedIn game, or Overall over all games); a
 * game with no LinkedIn feed, like Strands, never says LinkedIn under its rows.
 */
internal data class RanksBoardSlice(
    val title: String,
    val order: RankOrder,
    val state: DailyBoardState,
    val caption: String? = null,
    val note: String? = null,
    val friendsAlone: Boolean = false,
    val freshness: String? = null,
    val linkedIn: Boolean = true,
) {
    /** The hint under a Friends board of the viewer alone, null when the board is not. */
    val friendsHint: String? get() = if (friendsAlone) friendsHintText(linkedIn) else null
}

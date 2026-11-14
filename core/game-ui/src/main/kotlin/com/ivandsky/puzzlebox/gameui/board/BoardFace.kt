package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.rankRuleText

/**
 * One board as [DailyBoard] draws it, for a morph between two (see [BoardMorph]): the same [title], [order], [state],
 * [caption] and [ghosts] the board takes when it is the only one.
 */
data class BoardFace(
    val title: String,
    val order: RankOrder,
    val state: DailyBoardState,
    val caption: String = rankRuleText(order),
    val ghosts: List<GhostRowModel> = emptyList(),
)

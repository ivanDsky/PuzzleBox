package com.ivandsky.puzzlebox.profile.board

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId

/** One game's lifetime line, its [best] ranked by [rankOrder]; a tap opens History's timeline (N3). */
data class GameRow(
    val gameId: GameId,
    val name: String,
    val brand: GameBrand,
    val solved: Int,
    val best: BestScore?,
    val rankOrder: RankOrder,
)

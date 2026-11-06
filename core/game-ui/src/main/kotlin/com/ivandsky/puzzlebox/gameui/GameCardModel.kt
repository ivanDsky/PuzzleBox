package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Immutable
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId

/**
 * The plain inputs one game card renders. [status] null means today's status is still
 * loading, so the card draws skeletons in its slots. [rankOrder] has no default: a card
 * that dropped its game's order would format the best time-first by accident.
 */
@Immutable
data class GameCardModel(
    val game: GameId,
    val name: String,
    val brand: GameBrand,
    val status: TodayStatus?,
    val rankOrder: RankOrder,
    val puzzleNumber: Int? = null,
    val best: BestScore? = null,
)

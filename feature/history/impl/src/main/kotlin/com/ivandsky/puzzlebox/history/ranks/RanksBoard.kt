package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId

/** One board of the Ranks hub: the sum over every game, or one daily game's own board. */
internal sealed interface RanksBoard {
    data object Overall : RanksBoard

    data class Game(val id: GameId) : RanksBoard
}

/**
 * The hub's boards in swipe order: Overall, then every listed game in registration order. The one
 * list every consumer reads (swipe, dots, title menu, tablet list, restore), so a new daily game
 * shows up everywhere with no hub change.
 */
internal fun rankBoards(registry: GameRegistry): List<RanksBoard> =
    listOf(RanksBoard.Overall) + registry.all.map { RanksBoard.Game(it.definition.id) }

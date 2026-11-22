package com.ivandsky.puzzlebox.play.domain.shelves

import com.ivandsky.puzzlebox.model.GameId

/** The game shelf order of a player with no play history (R5). */
val DEFAULT_GAME_SHELF_ORDER: List<GameId> = listOf("queens", "zip", "tango", "minisudoku", "strands").map(::GameId)

/**
 * The order of the game shelves (R5): the games in [mostPlayedFirst] (the player's play history, most played game
 * first) that are in [games], then the rest of [games] in their own order. With no history that is [games] as given,
 * [DEFAULT_GAME_SHELF_ORDER] unless the caller passes the registry's list.
 */
fun gameShelfOrder(
    mostPlayedFirst: List<GameId>,
    games: List<GameId> = DEFAULT_GAME_SHELF_ORDER,
): List<GameId> {
    val played = mostPlayedFirst.distinct().filter { it in games }
    return played + (games.distinct() - played.toSet())
}

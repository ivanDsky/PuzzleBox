package com.ivandsky.puzzlebox.gameui.trainer

import com.ivandsky.puzzlebox.model.GameId

/**
 * The Trainer adapters the app bound, in the order the Train tab lists them. Each game's UI module
 * binds its own adapter, so [all] holds the games that have one.
 *
 * The app module passes `TRAINER_ORDER` (Queens, Tango, Mini Sudoku) as [order]; the registry keeps
 * its own order because DI hands over adapters in no stable order.
 *
 * @param games the bound adapters, in any order
 * @param order the games to list first, in this order; an id with no adapter is skipped, and an adapter not named here follows, sorted by game id
 */
class TrainerRegistry(games: List<TrainerGame>, order: List<GameId>) {
    private val byId: Map<GameId, TrainerGame> = buildMap {
        games.forEach { game ->
            val id = game.gameId
            require(put(id, game) == null) { "trainer already registered for ${id.value}" }
        }
    }

    init {
        val seen = HashSet<GameId>()
        order.forEach { id -> require(seen.add(id)) { "trainer order lists ${id.value} twice" } }
    }

    /** The adapters in [order], then the ones [order] does not name, sorted by game id. */
    val all: List<TrainerGame> = order.mapNotNull { byId[it] } +
        byId.values.filter { it.gameId !in order }.sortedBy { it.gameId.value }

    /** True when no adapter is bound: the Train tab shows only when it is false. */
    val isEmpty: Boolean get() = all.isEmpty()

    /** The adapter for [id], or null when its game has none bound. */
    operator fun get(id: GameId): TrainerGame? = byId[id]
}

package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.GameId

/**
 * The app's registered games (ADR-0001), in registration order — the ONE lookup every
 * consumer uses instead of aggregating plugins and scanning for an id. `app` builds it
 * once from every game module's Koin-registered [GamePlugin].
 */
class GameRegistry(plugins: List<GamePlugin<*, *, *>>) {

    /** Every registered game, registration order (Home's featured game is the first). */
    val all: List<GamePlugin<*, *, *>> = plugins.toList()

    private val byId: Map<GameId, GamePlugin<*, *, *>> = all.associateBy { it.definition.id }

    /** The registered plugin for [id]; null for a game this build does not ship. */
    operator fun get(id: GameId): GamePlugin<*, *, *>? = byId[id]

    /** [id]'s display name; the raw id for a game this build does not ship. */
    fun displayName(id: GameId): String = this[id]?.definition?.displayName ?: id.value
}

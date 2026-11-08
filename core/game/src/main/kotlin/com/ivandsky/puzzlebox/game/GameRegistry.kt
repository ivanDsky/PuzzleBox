package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey

/**
 * The app's registered games (ADR-0001), in registration order — the ONE lookup every
 * consumer uses instead of aggregating plugins and scanning for an id. `app` builds it
 * once from every game module's Koin-registered [GamePlugin].
 *
 * A game in [unlisted] ships and opens by id ([get]) but stays off every surface that
 * reads [all] — hub, Home, History, stats, Profile (ADR-0015: the debug 9x9 Sudoku).
 *
 * The id `custom` ([PuzzleKey.Custom.NAMESPACE]) is reserved (ADR-0018): custom ref keys
 * start with it, so no plugin, listed or unlisted, may claim it.
 */
class GameRegistry(
    plugins: List<GamePlugin<*, *, *>>,
    unlisted: Set<GameId> = emptySet(),
) {

    init {
        require(plugins.none { it.definition.id.value == PuzzleKey.Custom.NAMESPACE }) {
            "GameId '${PuzzleKey.Custom.NAMESPACE}' is reserved"
        }
    }

    /** Every listed game, registration order (Home's featured game is the first). */
    val all: List<GamePlugin<*, *, *>> = plugins.filter { it.definition.id !in unlisted }

    /** Registered games no surface lists; reachable only by id (debug settings). */
    val unlisted: List<GamePlugin<*, *, *>> = plugins.filter { it.definition.id in unlisted }

    private val byId: Map<GameId, GamePlugin<*, *, *>> = plugins.associateBy { it.definition.id }

    /** The registered plugin for [id], listed or not; null for a game this build does not ship. */
    operator fun get(id: GameId): GamePlugin<*, *, *>? = byId[id]

    /** [id]'s display name; the raw id for a game this build does not ship. */
    fun displayName(id: GameId): String = this[id]?.definition?.displayName ?: id.value

    /** The game's ranking rule; [RankOrder.TimeThenHints] for an unknown id. */
    fun rankOrderOf(id: GameId): RankOrder = this[id]?.score?.rankOrder ?: RankOrder.TimeThenHints
}

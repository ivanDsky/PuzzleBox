package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.GameRegistry

/** Pairs daily sources with the registry's plugins; names no game. */
object IndexTargets {

    /**
     * Each of [sources] paired with the [registry] plugin of the same game id, keeping only those
     * whose plugin has a technique walker. A source with no plugin, or a plugin without a walker, is
     * left out, so a game joins the pass by itself the day its plugin gets a walker.
     */
    fun pair(sources: List<DailySource>, registry: GameRegistry): List<IndexTarget> =
        sources.mapNotNull { source ->
            val plugin = registry[source.gameId] ?: return@mapNotNull null
            val walker = plugin.techniqueWalker ?: return@mapNotNull null
            val zone = when (val cadence = plugin.definition.cadence) {
                is Cadence.Daily -> cadence.zone
            }
            IndexTarget(source, walker, zone)
        }
}

package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId

/** Pure projection of one game's identity — built from its plugin definition. */
data class GameSummary(
    val id: GameId,
    val displayName: String,
)

/**
 * The featured game — Home's Today card and Profile's leaderboard board: the first
 * registered plugin. Every current game is daily; cadence-driven selection lands with a
 * second cadence. An empty registry is a wiring bug (DiGraphTest enforces ≥1); the
 * constant is the fallback so a graph that registers no plugins still composes.
 */
class GetFeaturedGame(private val registry: GameRegistry) {

    operator fun invoke(): GameSummary =
        registry.all.firstOrNull()?.let {
            GameSummary(id = it.definition.id, displayName = it.definition.displayName)
        } ?: GameSummary(id = GameId("zip"), displayName = "Zip")
}

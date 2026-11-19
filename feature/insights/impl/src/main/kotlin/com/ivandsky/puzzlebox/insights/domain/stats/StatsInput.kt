package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.insights.domain.recap.PeriodFacts
import com.ivandsky.puzzlebox.model.GameId

/**
 * What every stats section provider reads: the [facts] of the range and scope the page is on, the [range] itself and
 * the app's [games] in the order a section lists them. The facts are the one fold's ([PeriodFacts]), never a second.
 */
internal data class StatsInput(
    val range: StatsRange,
    val facts: PeriodFacts,
    val games: List<GameId>,
) {

    /** [byGame]'s entries in the app's game order; a game the app no longer lists follows by id. */
    fun <T> inGameOrder(byGame: Map<GameId, T>): List<Pair<GameId, T>> {
        val unlisted = (byGame.keys - games.toSet()).sortedBy { it.value }
        return (games + unlisted).mapNotNull { game -> byGame[game]?.let { game to it } }
    }
}

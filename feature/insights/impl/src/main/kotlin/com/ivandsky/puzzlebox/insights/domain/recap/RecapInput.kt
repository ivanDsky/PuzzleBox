package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId

/**
 * What every recap fact provider reads: the period's [facts], the app's [games] in the order a card lists them, and the
 * [friends] answer, which is [FriendsStanding.Reading] until the friends read lands and only the friends card looks at.
 */
internal data class RecapInput(
    val facts: PeriodFacts,
    val games: List<GameId>,
    val friends: FriendsStanding = FriendsStanding.Reading,
) {

    /**
     * [byGame]'s entries in the app's game order; a game the app no longer lists follows by id, so a card's rows still
     * cover everything the period counted.
     */
    fun <T> inGameOrder(byGame: Map<GameId, T>): List<Pair<GameId, T>> {
        val unlisted = (byGame.keys - games.toSet()).sortedBy { it.value }
        return (games + unlisted).mapNotNull { game -> byGame[game]?.let { game to it } }
    }
}

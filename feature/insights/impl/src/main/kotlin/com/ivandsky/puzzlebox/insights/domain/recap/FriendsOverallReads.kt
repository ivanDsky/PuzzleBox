package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** A Friends Overall read ([com.ivandsky.puzzlebox.domain.ranks.ObserveFriendsOverall]) of the [RankWindow]'s boards of some games. */
internal typealias FriendsOverallRead = (RankWindow, List<GameId>) -> Flow<OverallState>

/**
 * The one way insights reads Friends Overall per game, for the recap's friends card and the stats page's vs friends:
 * [game] over [window] once its read has answered for good, so not still loading and not a half answer that more boards
 * could change.
 */
internal suspend fun FriendsOverallRead.settled(window: RankWindow, game: GameId): OverallState =
    this(window, listOf(game)).first { it != OverallState.Loading && it !is OverallState.Partial }

/** Each of [games] read with [settled], together, paired with its game in the order given. */
internal suspend fun FriendsOverallRead.settledEach(window: RankWindow, games: List<GameId>): List<Pair<GameId, OverallState>> =
    coroutineScope { games.map { game -> async { game to settled(window, game) } }.awaitAll() }

/** The standings rows of a read that loaded; none for any other state. */
internal fun OverallState.rows(): List<OverallStandings.Row> = (this as? OverallState.Complete)?.standings?.rows.orEmpty()

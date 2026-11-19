package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Games
import com.ivandsky.puzzlebox.model.GameId

/**
 * What a [Games] pick leaves of the hub's boards: Overall, and under LinkedIn games only the games [isLinkedInGame] says
 * are fed from LinkedIn, so Strands leaves the list the swipe, the title menu and the tablet's list walk.
 */
internal fun List<RanksBoard>.listed(games: Games, isLinkedInGame: (GameId) -> Boolean): List<RanksBoard> = when (games) {
    Games.All -> this
    Games.LinkedIn -> filter { it !is RanksBoard.Game || isLinkedInGame(it.id) }
}

/**
 * The window boards a [Games] pick counts: under LinkedIn games the boards of the other games drop out of what Overall
 * sums, so its totals, games played and "x of y" count the LinkedIn games only. They are the boards already read, never more.
 */
internal fun RankWindowBoards.among(games: Games, isLinkedInGame: (GameId) -> Boolean): RankWindowBoards = when {
    games == Games.All || this !is RankWindowBoards.Boards -> this
    else -> RankWindowBoards.Boards(boards.filter { isLinkedInGame(GameId(it.board.gameId)) })
}

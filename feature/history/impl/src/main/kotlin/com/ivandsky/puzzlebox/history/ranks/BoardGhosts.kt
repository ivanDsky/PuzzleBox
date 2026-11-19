package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardRead
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.ranks.BoardLoad
import com.ivandsky.puzzlebox.domain.ranks.CountedBoards
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.domain.ranks.connectionUid
import com.ivandsky.puzzlebox.domain.ranks.unplacedConnections
import com.ivandsky.puzzlebox.model.GameId

/**
 * What [game]'s one day of LinkedIn connections, from the phone's own saved data, adds to its Day board, the
 * connections without a place being the ones the domain leaves off the Friends board; a Day window holds one day a game.
 */
internal fun CountedBoards.ghostInput(game: GameId, viewerUid: String?): BoardGhostInput {
    val (board, day) = days.entries.firstOrNull { it.key.gameId == game.value } ?: return BoardGhostInput.None
    val friends = (plain as? RankWindowBoards.Boards)?.boards?.firstOrNull { it.board == board } as? BoardLoad.Loaded
    return day.toGhostInput(friends?.page?.unplacedConnections(day, viewerUid).orEmpty())
}

/**
 * When [game]'s one day had its LinkedIn board last read, null while the link is not connected (the board is a relic
 * then) or no board was read.
 */
internal fun CountedBoards.boardRead(game: GameId): LinkedInBoardRead? =
    days.entries.firstOrNull { it.key.gameId == game.value }?.value?.takeIf { it.status != LinkedInDayStatus.NotConnected }?.boardRead

/** Every counted connection's cached photo, by the uid its row is ranked under. */
internal fun CountedBoards.photos(): Map<String, String?> = days.values
    .flatMap { (it.board as? LinkedInBoardView.Shown)?.ghosts.orEmpty() }
    .associate { connectionUid(it.memberRef) to it.photoPath }

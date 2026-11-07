package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/** One friend's puzzle in From friends: its [code], game, [title], [authorName] and [status]. */
data class FriendPuzzleRow(val code: ShareCode, val gameId: GameId, val title: String, val authorName: String, val status: FriendStatus)

package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry

/** One row of a friend puzzle's board: its 1-based [rank] among the players, and whether it is this user's. */
data class RankedEntry(val rank: Int, val entry: LeaderboardEntry, val isMe: Boolean)

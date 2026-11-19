package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView

/**
 * A scope's boards as it ranks them without connections ([plain]) and with the connections of their days ranked
 * among them ([counted]), the saved [days] being what was merged. Without connections the two are the same boards.
 */
data class CountedBoards(val plain: RankWindowBoards, val counted: RankWindowBoards, val days: Map<LeaderboardRef, LinkedInDayView>)

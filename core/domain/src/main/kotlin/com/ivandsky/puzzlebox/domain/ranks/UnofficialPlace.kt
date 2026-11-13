package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * Where a device-only score would sit on the [official] board: 1 plus the official entries
 * strictly better, so a tie shares the place of the equal entry. It takes no place from
 * anyone (A14).
 */
fun unofficialPlace(timeMs: Long, hints: Int, official: List<LeaderboardEntry>, order: RankOrder): Int {
    val score = BestScore(timeMs, hints)
    return 1 + official.count { order.comparator.compare(BestScore(it.timeMs, it.hints), score) < 0 }
}

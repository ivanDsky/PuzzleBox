package com.ivandsky.puzzlebox.play.domain

import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.isSolvedDaily
import com.ivandsky.puzzlebox.model.PlaySlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * The tablet play side panel's data for one [PlaySlot]: today's set ([ObserveTodaySet]),
 * whether the slot is solved off the merged history, and the slot's own board through [ObserveLeaderboard]'s
 * identity gate — the slot's game, not the featured one, since every daily is posted.
 * The board emits its loading state at once, so the local parts never wait on the network.
 * A [PlaySlot.Custom] or [PlaySlot.Leg] puzzle has no daily board: its [PlayStats.board] is
 * null and it is never [PlayStats.solved].
 */
class ObservePlayStats(
    private val todaySet: ObserveTodaySet,
    private val history: ObserveGameHistory,
    private val leaderboard: ObserveLeaderboard,
) {

    operator fun invoke(slot: PlaySlot): Flow<PlayStats> = combine(
        todaySet(),
        history(),
        when (slot) {
            is PlaySlot.Daily -> leaderboard(slot.gameId, slot.day)
            is PlaySlot.Custom, is PlaySlot.Leg -> flowOf(null)
        },
    ) { set, days, board ->
        val own = days.filter { it.gameId == slot.gameId }
        PlayStats(
            solvedToday = set.count { it.status is TodayStatus.Solved },
            gamesToday = set.size,
            board = board,
            solved = slot is PlaySlot.Daily && own.any { it.day == slot.day && it.isSolvedDaily() },
        )
    }.distinctUntilChanged()
}

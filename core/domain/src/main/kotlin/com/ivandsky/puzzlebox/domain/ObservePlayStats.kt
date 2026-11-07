package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.model.PlaySlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * The tablet play side panel's data for one [PlaySlot] (A15): today's set ([ObserveTodaySet]),
 * the game's stats ([ObserveGameStats]) and streak days off the merged history, the game's
 * day ([GameCalendar.observeToday]), and the slot's own board through [ObserveLeaderboard]'s
 * identity gate — the slot's game, not the featured one, since every daily is posted.
 * The board emits its loading state at once, so the local parts never wait on the network.
 * A [PlaySlot.Custom] puzzle has no daily board: its [PlayStats.board] is null and it is
 * never [PlayStats.solved].
 */
class ObservePlayStats(
    private val todaySet: ObserveTodaySet,
    private val stats: ObserveGameStats,
    private val history: ObserveGameHistory,
    private val calendar: GameCalendar,
    private val leaderboard: ObserveLeaderboard,
) {

    operator fun invoke(slot: PlaySlot): Flow<PlayStats> = combine(
        todaySet(),
        stats(slot.gameId),
        history(),
        calendar.observeToday(slot.gameId),
        when (slot) {
            is PlaySlot.Daily -> leaderboard(slot.gameId, slot.day)
            is PlaySlot.Custom -> flowOf(null)
        },
    ) { set, game, days, today, board ->
        val own = days.filter { it.gameId == slot.gameId }
        PlayStats(
            solvedToday = set.count { it.status is TodayStatus.Solved },
            gamesToday = set.size,
            stats = game,
            streakDays = own.filter { it.isStreakDay() }.mapTo(HashSet()) { it.day },
            today = today,
            board = board,
            solved = slot is PlaySlot.Daily && own.any { it.day == slot.day && it.isSolvedDaily() },
        )
    }.distinctUntilChanged()
}

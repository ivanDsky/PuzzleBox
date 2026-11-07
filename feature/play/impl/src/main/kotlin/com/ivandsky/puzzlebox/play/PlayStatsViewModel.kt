package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.model.PlaySlot
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * The tablet play side panel's state holder (A15) for one [slot]: today's set
 * ([ObserveTodaySet]), the game's stats ([ObserveGameStats]: streak, best, average), its
 * last seven days off [ObserveGameHistory] for the week strip, and the slot's board.
 *
 * The board mirrors [com.ivandsky.puzzlebox.domain.ObserveTodayLeaderboard]'s identity
 * gate, but reads the slot's own game and day: that use case only observes the featured
 * game, while the submit watcher posts every game's dailies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayStatsViewModel(
    private val slot: PlaySlot,
    registry: GameRegistry,
    private val brands: GamePlayRegistry,
    calendar: GameCalendar,
    todaySet: ObserveTodaySet,
    stats: ObserveGameStats,
    history: ObserveGameHistory,
    identity: IdentityService,
    leaderboard: LeaderboardRepository,
) : ViewModel() {

    private val initial = PlayStatsState(
        gameName = registry[slot.gameId]?.definition?.displayName.orEmpty(),
        solvedToday = null,
        gamesToday = registry.all.size,
        streak = 0,
        // Seven empty days, so the strip holds its height while history loads.
        week = week(emptyList(), calendar.today(slot.gameId) ?: (slot as? PlaySlot.Daily)?.day ?: LocalDate.now()),
        best = null,
        rankOrder = registry[slot.gameId]?.score?.rankOrder ?: RankOrder.TimeThenHints,
        averageMs = null,
        times = PlayTimes.Loading,
    )

    // A custom puzzle has no daily board.
    private val times: Flow<PlayTimes> = if (slot !is PlaySlot.Daily) flowOf(PlayTimes.Unavailable) else identity.current.flatMapLatest { player ->
        if (player == null) {
            flowOf(PlayTimes.SignedOut)
        } else {
            leaderboard.observe(LeaderboardRef(slot.gameId.value, slot.day))
                .map { snapshot -> snapshot?.let { board(it, player.uid) } ?: PlayTimes.Unavailable }
                .onStart { emit(PlayTimes.Loading) }
        }
    }

    internal val state: StateFlow<PlayStatsState> = combine(
        todaySet(),
        stats(slot.gameId),
        history(),
        calendar.observeToday(slot.gameId),
        times,
    ) { set, game, days, today, board ->
        initial.copy(
            solvedToday = set.count { it.status is TodayStatus.Solved },
            gamesToday = set.size,
            streak = game.currentStreak,
            week = week(days, today),
            best = game.best,
            rankOrder = game.rankOrder,
            averageMs = game.averageTimeMs,
            times = board,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    /** The seven days ending [today], each ringed when this game's daily was solved then;
     *  archived solves never count, as they never move the streak. */
    private fun week(days: List<GameHistoryDay>, today: LocalDate): List<WeekDay> {
        val solved = days
            .filter { it.gameId == slot.gameId && it.record != null && it.puzzleNumber != null && !it.archived }
            .mapTo(HashSet()) { it.day }
        val brand = listOf(brands.brandOf(slot.gameId))
        return (WEEK_DAYS - 1 downTo 0L).map { back ->
            val date = today.minusDays(back)
            WeekDay(date, if (date in solved) brand else emptyList(), isToday = back == 0L)
        }
    }

    private fun board(snapshot: LeaderboardSnapshot, uid: String): PlayTimes.Board =
        PlayTimes.Board(
            snapshot.entries.mapIndexed { index, entry ->
                PlayTimeRow(rank = index + 1, name = entry.displayName, timeMs = entry.timeMs, you = entry.uid == uid)
            },
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val WEEK_DAYS = 7L
    }
}

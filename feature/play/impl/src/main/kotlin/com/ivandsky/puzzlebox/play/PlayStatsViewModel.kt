package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.domain.ObservePlayStats
import com.ivandsky.puzzlebox.domain.PlayStats
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.model.PlaySlot
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The tablet play side panel's state holder (A15) for one [slot]: maps [ObservePlayStats]
 * to what the panel draws, the week strip in this game's brand.
 */
class PlayStatsViewModel(
    private val slot: PlaySlot,
    private val registry: GameRegistry,
    private val brands: GamePlayRegistry,
    calendar: GameCalendar,
    observePlayStats: ObservePlayStats,
    releaseFlags: ReleaseFlags,
) : ViewModel() {

    private val initial = PlayStatsState(
        gameName = registry[slot.gameId]?.definition?.displayName.orEmpty(),
        solvedToday = null,
        gamesToday = registry.all.size,
        streak = null,
        // Seven empty days, so the strip holds its height while history loads.
        week = week(emptySet(), calendar.today(slot.gameId) ?: (slot as? PlaySlot.Daily)?.day ?: LocalDate.now()),
        best = null,
        rankOrder = registry[slot.gameId]?.score?.rankOrder ?: RankOrder.TimeThenHints,
        averageMs = null,
        times = PlayTimes.Loading,
    )

    internal val state: StateFlow<PlayStatsState> = combine(observePlayStats(slot), releaseFlags.enabled) { stats, flags ->
        toState(stats, ReleaseFlag.PerGameLeaderboards in flags)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    private fun toState(stats: PlayStats, perGame: Boolean): PlayStatsState = initial.copy(
        solvedToday = stats.solvedToday,
        gamesToday = stats.gamesToday,
        streak = stats.stats.currentStreak,
        week = week(stats.streakDays, stats.today),
        best = stats.stats.best,
        rankOrder = stats.stats.rankOrder,
        averageMs = stats.stats.averageTimeMs,
        times = times(stats.board, stats.solved, perGame),
    )

    /** The seven days ending [today], each ringed when it is one of this game's [streakDays]. */
    private fun week(streakDays: Set<LocalDate>, today: LocalDate): List<WeekDay> {
        val brand = listOf(brands.brandOf(slot.gameId))
        return (WEEK_DAYS - 1 downTo 0L).map { back ->
            val date = today.minusDays(back)
            WeekDay(date, if (date in streakDays) brand else emptyList(), isToday = back == 0L)
        }
    }

    private fun times(board: TodayLeaderboard?, solved: Boolean, perGame: Boolean): PlayTimes = when (board) {
        null -> PlayTimes.Unavailable
        TodayLeaderboard.SignedOut -> PlayTimes.SignedOut
        is TodayLeaderboard.SignedIn -> when (val state = board.board) {
            TodayLeaderboard.Board.Loading -> PlayTimes.Loading
            TodayLeaderboard.Board.Unavailable -> PlayTimes.Unavailable
            is TodayLeaderboard.Board.Loaded -> rows(state.snapshot, board.identity.uid, solved, perGame)
        }
    }

    private fun rows(snapshot: LeaderboardSnapshot, uid: String, solved: Boolean, perGame: Boolean): PlayTimes.Board =
        PlayTimes.Board(
            rows = snapshot.entries.mapIndexed { index, entry ->
                PlayTimeRow(rank = index + 1, name = entry.displayName, timeMs = entry.timeMs, hints = entry.hints, you = entry.uid == uid)
            },
            solved = solved,
            order = if (perGame) registry.rankOrderOf(slot.gameId) else null,
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val WEEK_DAYS = 7L
    }
}

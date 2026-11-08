package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.leaderboard.inSubmitWindow
import com.ivandsky.puzzlebox.domain.ObservePlayStats
import com.ivandsky.puzzlebox.domain.PlayStats
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
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
import kotlinx.coroutines.flow.stateIn

/**
 * The tablet play side panel's state holder (A15) for one [slot]: maps [ObservePlayStats]
 * to what the panel draws, the week strip in this game's brand. With the per-game board on,
 * the player's post status from [posts] stands in for the plain Posting line.
 */
class PlayStatsViewModel(
    private val slot: PlaySlot,
    private val registry: GameRegistry,
    private val brands: GamePlayRegistry,
    private val calendar: GameCalendar,
    observePlayStats: ObservePlayStats,
    releaseFlags: ReleaseFlags,
    private val posts: SubmitStatusTracker,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val state: StateFlow<PlayStatsState> = combine(observePlayStats(slot), releaseFlags.enabled) { stats, flags ->
        stats to (ReleaseFlag.PerGameLeaderboards in flags)
    }
        .flatMapLatest { (stats, perGame) -> post(stats.board, perGame).map { post -> toState(stats, perGame, post) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    private fun toState(stats: PlayStats, perGame: Boolean, post: SubmitStatus?): PlayStatsState = initial.copy(
        solvedToday = stats.solvedToday,
        gamesToday = stats.gamesToday,
        streak = stats.stats.currentStreak,
        week = week(stats.streakDays, stats.today),
        best = stats.stats.best,
        rankOrder = stats.stats.rankOrder,
        averageMs = stats.stats.averageTimeMs,
        times = times(stats.board, stats.solved, perGame, post),
    )

    /** The signed-in player's post to the slot's daily board; null with the per-game board off. */
    private fun post(board: TodayLeaderboard?, perGame: Boolean): Flow<SubmitStatus?> {
        val daily = slot as? PlaySlot.Daily
        val uid = (board as? TodayLeaderboard.SignedIn)?.identity?.uid
        return if (!perGame || daily == null || uid == null) flowOf(null) else posts.observe(uid, LeaderboardRef(daily.gameId.value, daily.day))
    }

    /** The seven days ending [today], each ringed when it is one of this game's [streakDays]. */
    private fun week(streakDays: Set<LocalDate>, today: LocalDate): List<WeekDay> {
        val brand = listOf(brands.brandOf(slot.gameId))
        return (WEEK_DAYS - 1 downTo 0L).map { back ->
            val date = today.minusDays(back)
            WeekDay(date, if (date in streakDays) brand else emptyList(), isToday = back == 0L)
        }
    }

    private fun times(board: TodayLeaderboard?, solved: Boolean, perGame: Boolean, post: SubmitStatus?): PlayTimes = when (board) {
        null -> PlayTimes.Unavailable
        TodayLeaderboard.SignedOut -> PlayTimes.SignedOut
        is TodayLeaderboard.SignedIn -> when (val state = board.board) {
            TodayLeaderboard.Board.Loading -> PlayTimes.Loading
            TodayLeaderboard.Board.Unavailable -> PlayTimes.Unavailable
            is TodayLeaderboard.Board.Loaded -> rows(state.snapshot, board.identity.uid, solved, perGame, post)
        }
    }

    private fun rows(snapshot: LeaderboardSnapshot, uid: String, solved: Boolean, perGame: Boolean, post: SubmitStatus?): PlayTimes.Board {
        val daily = slot as? PlaySlot.Daily
        return PlayTimes.Board(
            rows = snapshot.entries.mapIndexed { index, entry ->
                PlayTimeRow(rank = index + 1, name = entry.displayName, timeMs = entry.timeMs, hints = entry.hints, you = entry.uid == uid)
            },
            solved = solved,
            order = if (perGame) registry.rankOrderOf(slot.gameId) else null,
            post = post,
            pastDay = daily != null && !calendar.inSubmitWindow(daily.gameId, daily.day),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val WEEK_DAYS = 7L
    }
}

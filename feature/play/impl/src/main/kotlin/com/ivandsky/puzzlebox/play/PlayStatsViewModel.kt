package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.leaderboard.inSubmitWindow
import com.ivandsky.puzzlebox.domain.ObservePlayStats
import com.ivandsky.puzzlebox.domain.PlayStats
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.board.BoardSignInHolder
import com.ivandsky.puzzlebox.play.linkedin.LinkedInBoardInput
import com.ivandsky.puzzlebox.play.linkedin.toBoardInput
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.resultui.board.DailyBoardLine
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.rankWithLinkedIn
import com.ivandsky.puzzlebox.resultui.board.withOwnPhoto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The tablet play side panel's state holder (A15) for one [slot]: maps [ObservePlayStats]
 * to what the panel draws: today's progress and the day's times, rank-ordered by the game's
 * rule. The player's post status from [posts] stands in for the plain Posting line. With [linkedIn] the slot day's
 * LinkedIn connections already on the phone rank into the times by the combined place; it only observes, never fetches.
 */
class PlayStatsViewModel(
    private val slot: PlaySlot,
    private val registry: GameRegistry,
    private val calendar: GameCalendar,
    observePlayStats: ObservePlayStats,
    private val posts: SubmitStatusTracker,
    identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    linkedIn: ObserveLinkedInDay?,
) : ViewModel() {

    private val signInHolder = BoardSignInHolder(identity, idTokens)

    /** How the in-place sign-in on the signed-out times card last went. */
    internal val signIn: StateFlow<BoardSignIn> = signInHolder.status

    /** The signed-out card's Google button: [activityContext] hosts Google's account picker. */
    fun signIn(activityContext: Context) {
        viewModelScope.launch { signInHolder.signIn(activityContext) }
    }

    private val initial = PlayStatsState(
        gameName = registry[slot.gameId]?.definition?.displayName.orEmpty(),
        solvedToday = null,
        gamesToday = registry.all.size,
        times = PlayTimes.Loading,
        linkedIn = linkedIn != null,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val state: StateFlow<PlayStatsState> = combine(observePlayStats(slot), linkedInInput(linkedIn), ::Pair)
        .flatMapLatest { (stats, input) -> post(stats.board).map { post -> toState(stats, post, input) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    /** The slot day's connections and the player's own import; none off a daily slot or with LinkedIn off. */
    private fun linkedInInput(linkedIn: ObserveLinkedInDay?): Flow<LinkedInBoardInput> {
        val daily = slot as? PlaySlot.Daily
        return if (daily == null || linkedIn == null) flowOf(LinkedInBoardInput()) else linkedIn(daily.gameId, daily.day).map { it.toBoardInput() }
    }

    private fun toState(stats: PlayStats, post: SubmitStatus?, input: LinkedInBoardInput): PlayStatsState = initial.copy(
        solvedToday = stats.solvedToday,
        gamesToday = stats.gamesToday,
        times = times(stats.board, stats.solved, post, input),
    )

    /** The signed-in player's post to the slot's daily board; null when none is observed. */
    private fun post(board: TodayLeaderboard?): Flow<SubmitStatus?> {
        val daily = slot as? PlaySlot.Daily
        val uid = (board as? TodayLeaderboard.SignedIn)?.identity?.uid
        return if (daily == null || uid == null) flowOf(null) else posts.observe(uid, LeaderboardRef(daily.gameId.value, daily.day))
    }

    private fun times(board: TodayLeaderboard?, solved: Boolean, post: SubmitStatus?, input: LinkedInBoardInput): PlayTimes = when (board) {
        null -> PlayTimes.Unavailable
        TodayLeaderboard.SignedOut -> PlayTimes.SignedOut
        is TodayLeaderboard.SignedIn -> when (val state = board.board) {
            TodayLeaderboard.Board.Loading -> PlayTimes.Loading
            TodayLeaderboard.Board.Unavailable -> PlayTimes.Unavailable
            is TodayLeaderboard.Board.Loaded -> rows(state.snapshot, board.identity, solved, post, input)
        }
    }

    private fun rows(snapshot: LeaderboardSnapshot, me: Identity, solved: Boolean, post: SubmitStatus?, input: LinkedInBoardInput): PlayTimes.Board {
        val daily = slot as? PlaySlot.Daily
        val order = registry.rankOrderOf(slot.gameId)
        val board = snapshot.entries.mapIndexed { index, entry ->
            DailyBoardRowModel(rank = index + 1, name = entry.displayName, timeMs = entry.timeMs, hints = entry.hints, isSelf = entry.uid == me.uid, playerId = entry.uid)
        }
        return PlayTimes.Board(
            rows = rankWithLinkedIn(board, input.ghosts, input.you, order).timed().withOwnPhoto(me.photoUrl),
            solved = solved,
            order = order,
            post = post,
            pastDay = daily != null && !calendar.inSubmitWindow(daily.gameId, daily.day),
        )
    }

    // A time list has no place for a connection whose time is hidden.
    private fun List<DailyBoardLine>.timed(): List<DailyBoardLine> = filterNot { it is DailyBoardLine.Ghost && it.place == null }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

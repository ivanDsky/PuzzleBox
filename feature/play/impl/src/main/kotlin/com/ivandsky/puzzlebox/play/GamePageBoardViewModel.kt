package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.toDailyBoardState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.board.BoardSignInHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Which day's board the game page shows; Today is first. */
internal enum class BoardDay { Today, Yesterday }

/**
 * What [GamePageBoard] draws: the [day] chosen and the [board] to draw for it, the top rows
 * with the player's own row pinned under them when it ranks lower. While the chosen day's
 * board has not arrived, [board] is Loading.
 */
internal data class GamePageBoardState(
    val day: BoardDay = BoardDay.Today,
    val board: DailyBoardState = DailyBoardState.Loading,
)

/**
 * One game's board on its page: today's or yesterday's, read through [leaderboard] so every
 * identity change restarts it, and sliced by [PlayTimes.Board]'s own top rule.
 * Sign-in goes through one [BoardSignInHolder], so the page's board signs in without leaving.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GamePageBoardViewModel(
    private val game: GameId,
    private val leaderboard: ObserveLeaderboard,
    private val calendar: GameCalendar,
    registry: GameRegistry,
    identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
) : ViewModel() {

    private val day = MutableStateFlow(BoardDay.Today)
    private val signInHolder = BoardSignInHolder(identity, idTokens)

    /** The game's display name, for the board's title. */
    val gameName: String = registry.displayName(game)

    /** The game's own rank rule. */
    val order: RankOrder = registry.rankOrderOf(game)

    val state: StateFlow<GamePageBoardState> =
        combine(day, boards()) { chosen, (drawnDay, board) ->
            // The chosen day is shown at once; another day's rows never stay under it while its board loads.
            if (drawnDay == chosen) stateOf(chosen, board) else GamePageBoardState(day = chosen)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GamePageBoardState())

    /** How this block's last sign-in went. */
    val signInStatus: StateFlow<BoardSignIn> = signInHolder.status

    fun selectDay(day: BoardDay) {
        this.day.value = day
    }

    /** The board's Google button: [activityContext] hosts Google's account picker, and is not kept. */
    suspend fun signIn(activityContext: Context) = signInHolder.signIn(activityContext)

    /** The chosen day's board, read again at each of the game's midnights; each emission names its day. */
    private fun boards() = day.flatMapLatest { chosen ->
        calendar.observeToday(game)
            .flatMapLatest { today -> leaderboard(game, if (chosen == BoardDay.Today) today else today.minusDays(1)) }
            .map { chosen to it }
    }

    private fun stateOf(day: BoardDay, board: TodayLeaderboard): GamePageBoardState {
        val drawn = board.toDailyBoardState()
        val ranked = (drawn as? DailyBoardState.Ranked)?.rows?.let { rows ->
            PlayTimes.Board(rows.map { PlayTimeRow(it.rank, it.name, it.timeMs, it.hints, it.isSelf) }, solved = false)
        } ?: return GamePageBoardState(day = day, board = drawn)
        return GamePageBoardState(
            day = day,
            board = DailyBoardState.Ranked(
                ranked.head.map { it.toRow() },
                pinned = ranked.pinned?.toRow(),
            ),
        )
    }

    private fun PlayTimeRow.toRow() = DailyBoardRowModel(rank, name, timeMs, hints, isSelf = you)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

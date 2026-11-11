package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel
import com.ivandsky.puzzlebox.gameui.board.toDailyBoardState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.board.BoardSignInHolder
import com.ivandsky.puzzlebox.play.linkedin.LinkedInDayState
import com.ivandsky.puzzlebox.play.linkedin.LinkedInDayState.Target
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Which relative day's board the game page shows while no calendar day is picked; Today is first. */
internal enum class BoardDay { Today, Yesterday }

/**
 * What [GamePageBoard] draws: the [day] chosen, or the past calendar day [picked] over it, and the [board]
 * to draw for it, the top rows with the player's own row pinned under them when it ranks lower. While the
 * chosen day's board has not arrived, [board] is Loading. [ghosts] are the LinkedIn connections of that day, none until they are read.
 */
internal data class GamePageBoardState(
    val day: BoardDay = BoardDay.Today,
    val picked: LocalDate? = null,
    val board: DailyBoardState = DailyBoardState.Loading,
    val ghosts: List<GhostRowModel> = emptyList(),
)

/**
 * One game's board on its page: today's, yesterday's or a picked past day's, read through [leaderboard] so every
 * identity change restarts it, and cut to the top rows with the player's own row pinned under them.
 * Sign-in goes through one [BoardSignInHolder], so the page's board signs in without leaving.
 * A non-null [linkedIn] adds the day's LinkedIn ghosts; its collection on Today or Yesterday is the only thing that
 * syncs that day, so the board asks LinkedIn nothing for a day nobody picked, and null touches nothing of LinkedIn.
 * A picked past day only reads what the phone holds, since the day card's own collection already syncs it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GamePageBoardViewModel(
    private val game: GameId,
    private val leaderboard: ObserveLeaderboard,
    private val calendar: GameCalendar,
    registry: GameRegistry,
    identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    private val linkedIn: LinkedInDayState?,
) : ViewModel() {

    private val choice = MutableStateFlow(Choice(BoardDay.Today, null))
    private val signInHolder = BoardSignInHolder(identity, idTokens)

    /** The game's display name, for the board's title. */
    val gameName: String = registry.displayName(game)

    /** The game's own rank rule. */
    val order: RankOrder = registry.rankOrderOf(game)

    val state: StateFlow<GamePageBoardState> =
        combine(choice, boards(), ghosts()) { chosen, (drawnChoice, board), (ghostChoice, ghosts) ->
            // The chosen day is shown at once; another day's rows or ghosts never stay under it while its data loads.
            if (drawnChoice == chosen) {
                stateOf(chosen, board).copy(ghosts = if (ghostChoice == chosen) ghosts else emptyList())
            } else {
                GamePageBoardState(day = chosen.day, picked = chosen.picked)
            }
        }
            // With LinkedIn on, no grace: a short visit must not leave a debounced sync running after the board is gone.
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(if (linkedIn != null) 0 else STOP_TIMEOUT_MS),
                GamePageBoardState(),
            )

    /** How this block's last sign-in went. */
    val signInStatus: StateFlow<BoardSignIn> = signInHolder.status

    fun selectDay(day: BoardDay) {
        choice.update { it.copy(day = day) }
    }

    /** The calendar's picked past [date], or null for none; a pick starts from Today, so unpicking returns to it. */
    fun pick(date: LocalDate?) {
        choice.update { if (date != null) Choice(BoardDay.Today, date) else it.copy(picked = null) }
    }

    /** The board's Google button: [activityContext] hosts Google's account picker, and is not kept. */
    suspend fun signIn(activityContext: Context) = signInHolder.signIn(activityContext)

    /** The chosen day's board, read again at each of the game's midnights; each emission names its choice. */
    private fun boards(): Flow<Pair<Choice, TodayLeaderboard>> = choice.flatMapLatest { chosen ->
        val picked = chosen.picked
        val read = if (picked != null) {
            leaderboard(game, picked)
        } else {
            calendar.observeToday(game)
                .flatMapLatest { today -> leaderboard(game, if (chosen.day == BoardDay.Today) today else today.minusDays(1)) }
        }
        read.map { chosen to it }
    }

    /**
     * The chosen day's LinkedIn ghosts; each emission names its choice. Starts empty so the board is not held back
     * by a read that arrives later than the leaderboard's. Without LinkedIn it is empty and touches nothing.
     */
    private fun ghosts(): Flow<Pair<Choice, List<GhostRowModel>>> {
        val holder = linkedIn ?: return flowOf(choice.value to emptyList())
        return choice.flatMapLatest { chosen ->
            val picked = chosen.picked
            val view = if (picked != null) {
                holder.observed(Target(game, picked))
            } else {
                holder.view(
                    calendar.observeToday(game)
                        .map { today -> Target(game, if (chosen.day == BoardDay.Today) today else today.minusDays(1)) },
                )
            }
            view
                .map { chosen to ghostsOf(it) }
                .onStart { emit(chosen to emptyList()) }
        }
    }

    private fun ghostsOf(view: LinkedInDayView?): List<GhostRowModel> =
        (view?.board as? LinkedInBoardView.Shown)?.ghosts.orEmpty().map { GhostRowModel(it.name, it.timeMs, it.hintFree) }

    /** The page's board: the first [TOP_ROWS] rows, with the player's own row pinned under them when it ranks lower. */
    private fun stateOf(chosen: Choice, board: TodayLeaderboard): GamePageBoardState {
        val drawn = board.toDailyBoardState()
        if (drawn !is DailyBoardState.Ranked) return GamePageBoardState(day = chosen.day, picked = chosen.picked, board = drawn)
        val own = drawn.rows.indexOfFirst { it.isSelf }
        return GamePageBoardState(
            day = chosen.day,
            picked = chosen.picked,
            board = drawn.copy(rows = drawn.rows.take(TOP_ROWS), pinned = if (own < TOP_ROWS) null else drawn.rows[own]),
        )
    }

    /** What the page asks for: the relative [day], or the calendar's [picked] past day over it. */
    private data class Choice(val day: BoardDay, val picked: LocalDate?)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TOP_ROWS = 5
    }
}

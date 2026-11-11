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
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel
import com.ivandsky.puzzlebox.gameui.board.rankWithGhosts
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * What [GamePageBoard] draws: the [day] shown (null until the first read is chosen), whether it [isToday] on the
 * game's calendar, and the [board] to draw for it, the top rows with the player's own row pinned under them when
 * it ranks lower. While that day's board has not arrived, [board] is Loading. [ghosts] are the LinkedIn
 * connections of that day, none until they are read.
 */
internal data class GamePageBoardState(
    val day: LocalDate? = null,
    val isToday: Boolean = true,
    val board: DailyBoardState = DailyBoardState.Loading,
    val ghosts: List<GhostRowModel> = emptyList(),
)

/**
 * One game's board on its page: the day the page's day card shows, read through [leaderboard] so every
 * identity change restarts it, and cut to the top rows with the player's own row pinned under them.
 * Sign-in goes through one [BoardSignInHolder], so the page's board signs in without leaving.
 * A non-null [linkedIn] adds that day's LinkedIn ghosts, only ever read from what the phone holds: the day card's
 * own collection already syncs the day it shows, so the board never asks LinkedIn anything. Null touches nothing of LinkedIn.
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

    private val choice = MutableStateFlow(Choice(picked = null, cardDay = null))
    private val signInHolder = BoardSignInHolder(identity, idTokens)

    /** The game's display name, for the board's title. */
    val gameName: String = registry.displayName(game)

    /** The game's own rank rule. */
    val order: RankOrder = registry.rankOrderOf(game)

    val state: StateFlow<GamePageBoardState> = shown()
        .flatMapLatest { shown ->
            // The shown day is named at once; another day's rows or ghosts never stay under it while its data loads.
            combine(leaderboard(game, shown.day), ghosts(shown.day)) { board, ghosts -> stateOf(shown, board, ghosts) }
                .onStart { emit(GamePageBoardState(day = shown.day, isToday = shown.isToday)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GamePageBoardState())

    /** How this block's last sign-in went. */
    val signInStatus: StateFlow<BoardSignIn> = signInHolder.status

    /**
     * The page's days: the calendar's [picked] past day, or null for none, over the day card's own [cardDay]
     * (null while it loads), so unpicking returns to the day card's day.
     */
    fun show(picked: LocalDate?, cardDay: LocalDate?) {
        choice.update { Choice(picked, cardDay) }
    }

    /** The board's Google button: [activityContext] hosts Google's account picker, and is not kept. */
    suspend fun signIn(activityContext: Context) = signInHolder.signIn(activityContext)

    /** The day to show, read again at each of the game's midnights: the pick, else the day card's day, else today. */
    private fun shown(): Flow<Shown> =
        combine(choice, calendar.observeToday(game)) { chosen, today ->
            val day = chosen.picked ?: chosen.cardDay ?: today
            Shown(day, isToday = day == today)
        }.distinctUntilChanged()

    /**
     * [day]'s LinkedIn ghosts as the phone holds them. Starts empty so the board is not held back
     * by a read that arrives later than the leaderboard's. Without LinkedIn it is empty and touches nothing.
     */
    private fun ghosts(day: LocalDate): Flow<List<GhostRowModel>> {
        val holder = linkedIn ?: return flowOf(emptyList())
        return holder.observed(Target(game, day)).map(::ghostsOf).onStart { emit(emptyList()) }
    }

    private fun ghostsOf(view: LinkedInDayView?): List<GhostRowModel> =
        (view?.board as? LinkedInBoardView.Shown)?.ghosts.orEmpty().map { GhostRowModel(it.memberRef, it.name, it.timeMs, it.hintFree, it.photoPath) }

    /**
     * The page's board: the first [TOP_ROWS] rows, with the player's own row pinned under them when it ranks lower.
     * With [ghosts] the rows and the ghosts are ranked together over the whole board before the cut, so a place
     * (the pinned row's too) counts every connection ahead of it, not only those among the rows drawn.
     */
    private fun stateOf(shown: Shown, board: TodayLeaderboard, ghosts: List<GhostRowModel>): GamePageBoardState {
        val drawn = board.toDailyBoardState()
        if (drawn !is DailyBoardState.Ranked) return GamePageBoardState(day = shown.day, isToday = shown.isToday, board = drawn, ghosts = ghosts)
        val own = drawn.rows.indexOfFirst { it.isSelf }
        val pinned = if (own < TOP_ROWS) null else drawn.rows[own]
        val ranked = if (ghosts.isEmpty()) null else rankWithGhosts(drawn.rows, ghosts, order)
        return GamePageBoardState(
            day = shown.day,
            isToday = shown.isToday,
            ghosts = ghosts,
            board = drawn.copy(
                rows = drawn.rows.take(TOP_ROWS),
                pinned = pinned,
                lines = ranked?.cutToTopRows(),
                pinnedPlace = if (pinned == null) null else ranked?.filterIsInstance<DailyBoardLine.Real>()?.first { it.row.isSelf }?.place,
            ),
        )
    }

    // Ghosts keep their places while the rows beyond the top are cut: the board draws the top and pins the player.
    private fun List<DailyBoardLine>.cutToTopRows(): List<DailyBoardLine> {
        var reals = 0
        return filter { it !is DailyBoardLine.Real || ++reals <= TOP_ROWS }
    }

    /** What the page asks for: the calendar's [picked] past day over the day card's own [cardDay]. */
    private data class Choice(val picked: LocalDate?, val cardDay: LocalDate?)

    /** A day on the board and whether it is the game calendar's today, which only the title tells apart. */
    private data class Shown(val day: LocalDate, val isToday: Boolean)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TOP_ROWS = 5
    }
}

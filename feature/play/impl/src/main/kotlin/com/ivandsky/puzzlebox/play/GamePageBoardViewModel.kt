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
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel
import com.ivandsky.puzzlebox.gameui.board.rankWithLinkedIn
import com.ivandsky.puzzlebox.gameui.board.toDailyBoardState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.board.BoardSignInHolder
import com.ivandsky.puzzlebox.play.linkedin.LinkedInBoardInput
import com.ivandsky.puzzlebox.play.linkedin.LinkedInDayState
import com.ivandsky.puzzlebox.play.linkedin.LinkedInDayState.Target
import com.ivandsky.puzzlebox.play.linkedin.toBoardInput
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
            combine(leaderboard(game, shown.day), linkedInInput(shown.day)) { board, input -> stateOf(shown, board, input) }
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
     * [day]'s LinkedIn ghosts and the player's own import as the phone holds them. Starts empty so the board is not
     * held back by a read that arrives later than the leaderboard's. Without LinkedIn it is empty and touches nothing.
     */
    private fun linkedInInput(day: LocalDate): Flow<LinkedInBoardInput> {
        val holder = linkedIn ?: return flowOf(LinkedInBoardInput())
        return holder.observed(Target(game, day)).map { it.toBoardInput() }.onStart { emit(LinkedInBoardInput()) }
    }

    /**
     * The page's board: the first [TOP_ROWS] rows, with the player's own row pinned under them when it ranks lower.
     * With LinkedIn [input] the rows and the ghosts are ranked together over the whole board before the cut, so a
     * place (the pinned row's too) counts every connection ahead of it, not only those among the rows drawn. The
     * player's own imported time and a same-name connection's time replace the PuzzleBox ones, and the rows follow
     * the times they show, each keeping its PuzzleBox rank.
     */
    private fun stateOf(shown: Shown, board: TodayLeaderboard, input: LinkedInBoardInput): GamePageBoardState {
        val drawn = board.toDailyBoardState()
        if (drawn !is DailyBoardState.Ranked) return GamePageBoardState(day = shown.day, isToday = shown.isToday, board = drawn, ghosts = input.ghosts)
        val ranked = if (input.ghosts.isEmpty() && input.own == null) null else rankWithLinkedIn(drawn.rows, input.ghosts, input.own, order)
        val reals = ranked?.filterIsInstance<DailyBoardLine.Real>()
        val rows = reals?.map { it.row } ?: drawn.rows
        val own = rows.indexOfFirst { it.isSelf }
        val pinned = if (own < TOP_ROWS) null else rows[own]
        return GamePageBoardState(
            day = shown.day,
            isToday = shown.isToday,
            ghosts = input.ghosts,
            board = drawn.copy(
                rows = rows.take(TOP_ROWS),
                pinned = pinned,
                lines = ranked?.cutToTopRows(),
                pinnedPlace = if (pinned == null) null else reals?.first { it.row.isSelf }?.place,
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

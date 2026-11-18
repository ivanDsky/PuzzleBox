package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView.Target
import com.ivandsky.puzzlebox.domain.ranks.FriendDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.board.BoardSignInHolder
import com.ivandsky.puzzlebox.play.linkedin.LinkedInBoardInput
import com.ivandsky.puzzlebox.play.linkedin.toBoardInput
import com.ivandsky.puzzlebox.resultui.board.DailyBoardLine
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.resultui.board.GhostRowModel
import com.ivandsky.puzzlebox.resultui.board.rankWithLinkedIn
import com.ivandsky.puzzlebox.resultui.board.toDailyBoardState
import com.ivandsky.puzzlebox.resultui.board.withOwnPhoto
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update

/**
 * What the game page's rank row reads: the [day] shown (null until the first read is chosen), whether it [isToday] on the
 * game's calendar, and the [board] to draw for it, the top rows with the player's own row pinned under them when
 * it ranks lower. While that day's board has not arrived, [board] is Loading, except that with LinkedIn a board
 * already drawn stays whole (day, rows and ghosts) until the next day is ready: the title and the rows change together.
 * [ghosts] are the LinkedIn connections of that day, none until they are read.  [entries] is how many players are on a Ranked
 * board, before the cut to the top rows (the rank summary's "of N"). In Friends scope (F20)
 * [amongFriends] is true and [standing] is the player's place among their friends and connections, null off the
 * board; the rows stay the whole board's.
 */
internal data class GamePageRankState(
    val day: LocalDate? = null,
    val isToday: Boolean = true,
    val board: DailyBoardState = DailyBoardState.Loading,
    val ghosts: List<GhostRowModel> = emptyList(),
    val entries: Int = 0,
    val amongFriends: Boolean = false,
    val standing: FriendDay.Standing? = null,
) {
    /** Whether rows or ghosts are on screen, so the next day can replace them rather than start from nothing. */
    fun isDrawn(): Boolean = board is DailyBoardState.Ranked || (board == DailyBoardState.SignedOut && ghosts.isNotEmpty())
}

/**
 * One game's board on its page: the day the page's day card shows, read through [leaderboard] so every
 * identity change restarts it, and cut to the top rows with the player's own row pinned under them.
 * Sign-in goes through one [BoardSignInHolder], so the page's board signs in without leaving.
 * A non-null [linkedIn] adds that day's LinkedIn ghosts, only ever read from what the phone holds: the day card's
 * own collection already syncs the day it shows, so the board never asks LinkedIn anything. Null touches nothing of LinkedIn.
 * A non-null [friendDay] makes the rank summary follow the player's Friends/Global setting (F20); null is Global.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GamePageRankViewModel(
    private val game: GameId,
    private val leaderboard: ObserveLeaderboard,
    private val calendar: GameCalendar,
    registry: GameRegistry,
    identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    private val linkedIn: ObserveLinkedInDayView?,
    private val friendDay: ObserveFriendDay?,
) : ViewModel() {

    private val choice = MutableStateFlow(Choice(picked = null, cardDay = null))
    private val signInHolder = BoardSignInHolder(identity, idTokens)

    /** The game's display name, for the board's title. */
    val gameName: String = registry.displayName(game)

    /** The game's own rank rule. */
    val order: RankOrder = registry.rankOrderOf(game)

    val state: StateFlow<GamePageRankState> = flow {
        // What the board last said, so a day switch can tell whether a board is on screen to keep.
        var said = GamePageRankState()
        emitAll(shown().flatMapLatest { shown -> statesOf(shown, held = said).onEach { said = it } })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GamePageRankState())

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
     * What [shown] says, [held] being what the board said before it. The title and the rows change together: with
     * LinkedIn and a board already drawn, [held] stays until the new day is ready (see [HeldDay]) and is then
     * replaced in one step; past [HOLD_CAP_MS] the day is named and shows Loading, as when nothing is drawn.
     * Within the day, every change flows through.
     */
    private fun statesOf(shown: Shown, held: GamePageRankState): Flow<GamePageRankState> {
        val loading = GamePageRankState(day = shown.day, isToday = shown.isToday)
        // No setting to follow (no reader bound) is Global.
        val scope: Flow<FriendDay?> = friendDay?.invoke(game, shown.day) ?: flowOf(null)
        val days: Flow<HeldDay.Arrived> = combine(leaderboard(game, shown.day), linkedInInput(shown.day), scope) { board, input, friends ->
            val drawn = board.toDailyBoardState()
            // Without LinkedIn, or a day that is not a board to wait for, nothing is read late.
            val ready = drawn != DailyBoardState.Loading && (drawn !is DailyBoardState.Ranked || input != null)
            // A leaderboard row has no photo: the player's own joins the signed-in account's, over a LinkedIn picture.
            val photo = (board as? TodayLeaderboard.SignedIn)?.identity?.photoUrl
            val state = stateOf(shown, drawn, input ?: LinkedInBoardInput()).let { it.copy(board = it.board.withOwnPhoto(photo)) }
            HeldDay.Arrived(if (friends == null) state else state.copy(amongFriends = true, standing = friends.standingOn(board)), ready)
        }
        if (linkedIn == null || !held.isDrawn()) return days.map { it.state }.onStart { emit(loading) }
        var released = false
        val cap: Flow<HeldDay> = flow { delay(HOLD_CAP_MS); emit(HeldDay.CapReached) }
        return merge(days, cap).transform { day ->
            when {
                released -> if (day is HeldDay.Arrived) emit(day.state)
                day is HeldDay.Arrived && day.ready -> { released = true; emit(day.state) }
                day is HeldDay.CapReached -> { released = true; emit(loading) }
            }
        }
    }

    /**
     * [day]'s LinkedIn ghosts and the player's own import as the phone holds them, null until the phone has
     * delivered its first read. Without LinkedIn it is empty and touches nothing.
     */
    private fun linkedInInput(day: LocalDate): Flow<LinkedInBoardInput?> {
        val holder = linkedIn ?: return flowOf(LinkedInBoardInput())
        return holder.observed(Target(game, day)).map<_, LinkedInBoardInput?> { it.toBoardInput() }.onStart { emit(null) }
    }

    /** The player's place among their friends and connections on a loaded [board], null off it or while it is not read. */
    private fun FriendDay.standingOn(board: TodayLeaderboard): FriendDay.Standing? {
        val signedIn = board as? TodayLeaderboard.SignedIn ?: return null
        val loaded = signedIn.board as? TodayLeaderboard.Board.Loaded ?: return null
        return standing(loaded.snapshot.entries, signedIn.identity.uid, order)
    }

    /**
     * The page's board: the first [TOP_ROWS] rows, with the player's own row pinned under them when it ranks lower.
     * With LinkedIn [input] the rows and the ghosts are ranked together over the whole board before the cut, so a
     * place (the pinned row's too) counts every connection ahead of it, not only those among the rows drawn. The
     * player's own imported time and a same-name connection's time replace the PuzzleBox ones, the player's row being
     * this profile's, else the one named like their LinkedIn name, else their LinkedIn row added; the rows follow the
     * times they show, each keeping its PuzzleBox rank.
     */
    private fun stateOf(shown: Shown, drawn: DailyBoardState, input: LinkedInBoardInput): GamePageRankState {
        if (drawn !is DailyBoardState.Ranked) {
            return GamePageRankState(day = shown.day, isToday = shown.isToday, board = drawn, ghosts = input.ghosts)
        }
        val ranked = if (input.ghosts.isEmpty() && input.you == null) null else rankWithLinkedIn(drawn.rows, input.ghosts, input.you, order)
        val reals = ranked?.filterIsInstance<DailyBoardLine.Real>()
        val rows = reals?.map { it.row } ?: drawn.rows
        val own = rows.indexOfFirst { it.isSelf }
        val pinned = if (own < TOP_ROWS) null else rows[own]
        return GamePageRankState(
            day = shown.day,
            isToday = shown.isToday,
            ghosts = input.ghosts,
            entries = rows.size,
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

    /** What one day reads while the board on screen is held for it. */
    private sealed interface HeldDay {
        /** The day's [state] from what has been read, and whether it is [ready] to replace the board on screen. */
        data class Arrived(val state: GamePageRankState, val ready: Boolean) : HeldDay

        data object CapReached : HeldDay
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** How long a board on screen waits for the next day to be ready before that day is named with Loading. */
        const val HOLD_CAP_MS = 1_500L
        const val TOP_ROWS = 5
    }
}

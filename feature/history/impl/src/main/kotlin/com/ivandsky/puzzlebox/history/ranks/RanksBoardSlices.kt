package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.inSubmitWindow
import com.ivandsky.puzzlebox.domain.ranks.BoardLoad
import com.ivandsky.puzzlebox.history.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.history.domain.ranks.OverallState
import com.ivandsky.puzzlebox.history.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.history.domain.ranks.windowStandings
import com.ivandsky.puzzlebox.history.domain.ranks.unofficialDays
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.board.withOwnPhoto
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn

private const val OVERALL_CAPTION = "Points from every game's Ranks. Higher is better."
private const val LINKEDIN_OVERALL_CAPTION = "Points from the LinkedIn games' Ranks. Higher is better."

/** A game's Week or Month caption when it adds up its days' points (WeekMonthPoints). */
internal const val WEEK_MONTH_POINTS_CAPTION = "Points from each day's Ranks. Higher is better."

/**
 * Every board of the hub as the slice its slot draws, in the hub's swipe order. All are projections of the hub's shared
 * window boards (the [window]'s boards as a scope ranks them, [scopedBoards], and Overall over the same), so scrubbing
 * to a neighbouring board reads nothing. Every slice of one list answers the same scope: in Friends each game and
 * Overall rank friends among themselves, with the LinkedIn connections ([ghosts]) counted on every one. With [freshness]
 * a game's Friends Day board also says when its LinkedIn part was last read, and the boards are drawn again each minute to keep it current.
 * With [weekMonthPoints] a game's Week and Month rank by its daily place points summed, as Overall sums every game's,
 * and say how many of the viewer's solved days are unofficial there, so add no day and no points to their row.
 * Under [RanksSelection.Games.LinkedIn] the list holds only the LinkedIn games ([isLinkedInGame]) and Overall sums only
 * their boards; each game's own board is the same, and nothing is read that the shared window boards do not already hold.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RanksBoardSlices(
    private val boards: List<RanksBoard>,
    private val window: Flow<RankWindow>,
    private val scopedBoards: (RankScope) -> Flow<Pair<RankWindow, RanksScopedBoards.Scoped>?>,
    private val observeOverall: ObserveOverall,
    private val viewer: Flow<Identity?>,
    private val history: Flow<List<HistoryDayRow>>,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
    private val isLinkedInGame: (GameId) -> Boolean,
    private val ghosts: BoardGhosts,
    private val freshness: RanksFreshness? = null,
    private val weekMonthPoints: Boolean = false,
) {
    private val gameIds = boards.filterIsInstance<RanksBoard.Game>().map { it.id }

    // A game's day turning over changes what its unofficial row may say, so it projects the boards again.
    private val rollover: Flow<Unit> = if (gameIds.isEmpty()) flowOf(Unit) else combine(gameIds.map(calendar::observeToday)) { }

    /** The boards of a [kind] window as [scope] ranks them, listed and counted as [games] says; [day] is the picked day their titles name, null for today. */
    fun observe(kind: RanksSelection.Window, day: LocalDate?, scope: RankScope, games: RanksSelection.Games): Flow<List<RanksBoardSlice>> = window.flatMapLatest { window ->
        // Null until this window's boards answer in this scope: neither the last window's nor the other scope's show as these.
        val answered = ghosts.counted(scope, scopedBoards(scope).map { it?.takeIf { (of, _) -> of == window }?.second }, viewer)
        // The line is only on a Friends Day board, so only there is a minute worth redrawing for.
        val updates = if (freshness != null && scope == RankScope.Friends && kind == RanksSelection.Window.Day) {
            combine(rollover, freshness.minutes) { _, _ -> }
        } else {
            rollover
        }
        fun drawn(inScope: Flow<BoardGhosts.Counted?>) =
            combine(inScope, observeOverall(inScope.mapNotNull { it?.counted?.among(games, isLinkedInGame) }), viewer, history, updates) { inWindow, overall, me, rows, _ ->
                slicesOf(kind, day, scope, games, inWindow, overall, me, rows)
            }
        if (scope == RankScope.Global) {
            drawn(answered)
        } else {
            // The boards and Overall read the same answer, so Friends listens to the phone's LinkedIn days once.
            channelFlow { drawn(answered.shareIn(this, SharingStarted.Eagerly, replay = 1)).collect { send(it) } }
        }
    }

    /**
     * A game's Day board in Friends draws its connections as ghost lines among the friends ([inFriends]); a Week or
     * Month and Overall draw them as rows ([inFriendsCounted]). Every place is the counted boards' own.
     */
    private fun slicesOf(
        kind: RanksSelection.Window,
        day: LocalDate?,
        scope: RankScope,
        games: RanksSelection.Games,
        inWindow: BoardGhosts.Counted?,
        overall: OverallState,
        me: Identity?,
        rows: List<HistoryDayRow>,
    ): List<RanksBoardSlice> {
        val friends = scope == RankScope.Friends
        val oneDay = kind == RanksSelection.Window.Day
        val photos = if (friends) inWindow?.photos().orEmpty() else emptyMap()
        val records = if (weekMonthPoints && !oneDay) rows.mapNotNull { it.record } else null
        // Leaderboard rows carry no photo: every slice's own row joins the signed-in account's, after the connections' pictures.
        return boards.listed(games, isLinkedInGame).map { board ->
            when (board) {
                RanksBoard.Overall -> overallSlice(kind, day, overall, me?.uid, if (games == RanksSelection.Games.LinkedIn) LINKEDIN_OVERALL_CAPTION else OVERALL_CAPTION)
                    .let { if (friends) it.inFriendsCounted(photos) else it }
                is RanksBoard.Game -> {
                    val slice = game(board.id, kind, day, if (oneDay) inWindow?.plain else inWindow?.counted, me, rows, records)
                    when {
                        !friends -> slice
                        oneDay -> slice.inFriends(inWindow, board.id, me?.uid).copy(freshness = inWindow?.boardRead(board.id)?.let { freshness?.line(it) })
                        else -> slice.inFriendsCounted(photos)
                    }
                }
            }
        }.map { it.copy(state = it.state.withOwnPhoto(me?.photoUrl)) }
    }

    /** With [records] (WeekMonthPoints on a Week or Month) the board ranks by points and says which of the viewer's solved days earn none. */
    private fun game(
        game: GameId,
        kind: RanksSelection.Window,
        day: LocalDate?,
        inWindow: RankWindowBoards?,
        me: Identity?,
        rows: List<HistoryDayRow>,
        records: List<CompletionRecord>?,
    ): RanksBoardSlice {
        val order = registry.rankOrderOf(game)
        val unofficial = inWindow.unofficialRow(game, order, me, rows) { calendar.inSubmitWindow(game, it) }.takeIf { kind == RanksSelection.Window.Day }
        val points = records?.let { pointsOf(me, it) }
        val notes = listOfNotNull(inWindow.gameBoardNote(game, kind), records?.let { unofficialNote(game, inWindow, me, it) })
        return RanksBoardSlice(
            title = boardTitle(registry.displayName(game), kind, day),
            order = order,
            state = inWindow.toGameBoardState(game, order, kind, me?.uid, points).withUnofficialRow(unofficial),
            caption = WEEK_MONTH_POINTS_CAPTION.takeIf { points != null },
            note = notes.joinToString(" · ").ifEmpty { null },
            linkedIn = isLinkedInGame(game),
        )
    }

    /** Overall's points over one game's days; the viewer's own [records] say which boards they solved officially past a full page (A11). */
    private fun pointsOf(me: Identity?, records: List<CompletionRecord>): (List<BoardLoad.Loaded>) -> OverallStandings =
        { loaded -> windowStandings(loaded, me, records) }

    /** The viewer's solved days of [game] that its points leave out, said: the days its Day board marks unofficial. */
    private fun unofficialNote(game: GameId, inWindow: RankWindowBoards?, me: Identity?, records: List<CompletionRecord>): String? {
        val loaded = (inWindow as? RankWindowBoards.Boards)?.boards.orEmpty().filterIsInstance<BoardLoad.Loaded>().filter { it.board.gameId == game.value }
        return unofficialDaysNote(unofficialDays(loaded, me, records) { calendar.inSubmitWindow(game, it.day) })
    }
}

/** Overall's slice of a [kind] window (titled for [day]) in [state], as [selfUid] sees it, under [caption]. */
internal fun overallSlice(kind: RanksSelection.Window, day: LocalDate?, state: OverallState, selfUid: String?, caption: String = OVERALL_CAPTION) = RanksBoardSlice(
    title = boardTitle("Overall", kind, day),
    order = RankOrder.TimeThenHints,
    state = state.toDailyBoardState(selfUid),
    caption = caption,
    note = state.boardNote(),
)

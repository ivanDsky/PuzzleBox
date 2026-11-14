package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.inSubmitWindow
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

private const val OVERALL_CAPTION = "Points from every game's board. Higher is better."

/**
 * Every board of the hub as the slice its slot draws, in the hub's swipe order. All of them are projections of what
 * the hub already holds (the [window]'s shared [windowBoards] and the [overallState] over them), so the board next to
 * the one on screen is there to scrub to, and holding them reads nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RanksBoardSlices(
    private val boards: List<RanksBoard>,
    private val window: Flow<RankWindow>,
    private val windowBoards: Flow<Pair<RankWindow, RankWindowBoards>?>,
    private val overallState: Flow<OverallState>,
    private val viewer: Flow<Identity?>,
    private val history: Flow<List<HistoryDayRow>>,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
) {
    private val games = boards.filterIsInstance<RanksBoard.Game>().map { it.id }

    // A game's day turning over changes what its unofficial row may say, so it projects the boards again.
    private val rollover: Flow<Unit> = if (games.isEmpty()) flowOf(Unit) else combine(games.map(calendar::observeToday)) { }

    /** The boards of a [kind] window; [day] is the picked day their titles name, null for today. */
    fun observe(kind: RanksSelection.Window, day: LocalDate?): Flow<List<RanksBoardSlice>> = window.flatMapLatest { window ->
        combine(windowBoards, overallState, viewer, history, rollover) { answered, overall, me, rows, _ ->
            val inWindow = answered?.takeIf { (of, _) -> of == window }?.second
            boards.map { board ->
                when (board) {
                    RanksBoard.Overall -> overallSlice(kind, day, overall, me?.uid)
                    is RanksBoard.Game -> game(board.id, kind, day, inWindow, me, rows)
                }
            }
        }
    }

    private fun game(
        game: GameId,
        kind: RanksSelection.Window,
        day: LocalDate?,
        inWindow: RankWindowBoards?,
        me: Identity?,
        rows: List<HistoryDayRow>,
    ): RanksBoardSlice {
        val order = registry.rankOrderOf(game)
        val unofficial = inWindow.unofficialRow(game, order, me, rows) { calendar.inSubmitWindow(game, it) }.takeIf { kind == RanksSelection.Window.Day }
        return RanksBoardSlice(
            title = boardTitle(registry.displayName(game), kind, day),
            order = order,
            state = inWindow.toGameBoardState(game, order, kind, me?.uid).withUnofficialRow(unofficial),
            note = inWindow.gameBoardNote(game, kind),
        )
    }
}

/** Overall's slice of a [kind] window (titled for [day]) in [state], as [selfUid] sees it. */
internal fun overallSlice(kind: RanksSelection.Window, day: LocalDate?, state: OverallState, selfUid: String?) = RanksBoardSlice(
    title = boardTitle("Overall", kind, day),
    order = RankOrder.TimeThenHints,
    state = state.toDailyBoardState(selfUid),
    caption = OVERALL_CAPTION,
    note = state.boardNote(),
)

package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.history.DayReplays
import com.ivandsky.puzzlebox.history.DayReplaysLoader
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryReplays
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * What the picked game board's day says about the viewer's own solve, both read off the one [selection] and the viewer's
 * [history]: [yourSolve] once they have solved it and [playThisDay] while they have not. Overall has neither.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RanksSolves(
    history: Flow<List<HistoryDayRow>>,
    selection: Flow<RanksSelection>,
    today: Flow<LocalDate>,
    daily: DailyRepository,
    playRegistry: GamePlayRegistry,
    loadReplay: LoadSolveReplay,
    scope: CoroutineScope,
) {
    private val dayReplays = DayReplaysLoader(scope, loadReplay)
    private val picked = selection.map { Triple(it.board, it.window, it.day) }.distinctUntilChanged()

    /** The viewer's solve of the game board's day: only on a game's Day board, and only once solved. */
    val yourSolve: Flow<YourSolve?> = combine(history, picked, today) { rows, (board, kind, day), today ->
        if (board !is RanksBoard.Game || kind != RanksSelection.Window.Day) {
            null
        } else {
            rows.solvedOn(board, day ?: today)
        }
    }.distinctUntilChanged().onEach(dayReplays::open).flatMapLatest { row ->
        if (row == null) flowOf(null) else dayReplays.replays.map { YourSolve(row, it.of(row), playRegistry[row.gameId]) }
    }

    /** What Play this day opens, if anything: the game board's day when the viewer has no solve of it (see [playThisDay]). */
    val playThisDay: Flow<PlayThisDay?> = combine(history, picked, today) { rows, (board, kind, picked), today ->
        val day = picked ?: today
        val solved = board is RanksBoard.Game && rows.solvedOn(board, day) != null
        playThisDay(board, kind, day, solved, (board as? RanksBoard.Game)?.let { daily.archiveRange(it.id) }, today)
    }.distinctUntilChanged()

    /** The viewer's solved row of [board]'s game on [day], the one rule behind both Your solve and Play this day. */
    private fun List<HistoryDayRow>.solvedOn(board: RanksBoard.Game, day: LocalDate): HistoryDayRow? =
        firstOrNull { it.gameId == board.id && it.day == day && it.record != null }

    /** The replays of [row]'s day only: a switch never flashes the last day's. */
    private fun DayReplays.of(row: HistoryDayRow): HistoryReplays = if (key == row.key) replays else HistoryReplays.Hidden
}

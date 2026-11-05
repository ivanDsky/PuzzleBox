package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.ObservePlayProgress
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.ResultKey
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The result route's live facts. [isRecord] is the A4 verdict for THIS play — null while
 * the write is in flight/unknown, or when nothing confirms it; [label] is null without a
 * live play; [writeFailed] (the retry affordance) is only ever true for a live play.
 */
data class ResultUiState(
    val isRecord: Boolean? = null,
    val writeFailed: Boolean = false,
    val label: String? = null,
)

/**
 * The result route's state holder for [key]'s slot. With the slot's play live
 * ([PlaySessionStore.peek]) the badge, label and record-write retry come from it. After
 * process death (no live play) the badge is a (time, hints) match against the slot's
 * completion records, and retry is hidden (A15, N20).
 *
 * [primary] (A7, SC10): on today's slot it is Next — [progress]'s next unsolved game after
 * this one, named by [games] — or AllDone when none is left, and null until progress first
 * lands, so the screen never shows a Done that turns into Next. Any other day's result (a
 * replay) offers Done from the first frame.
 */
class ResultViewModel(
    key: ResultKey,
    store: PlaySessionStore,
    completions: CompletionRepository,
    progress: ObservePlayProgress,
    games: GameRegistry,
    calendar: GameCalendar,
) : ViewModel() {

    private val slot = PlaySlot(GameId(key.gameId), LocalDate.parse(key.day))
    private val live: ActivePlay? = store.peek(slot)

    val state: StateFlow<ResultUiState> = if (live != null) {
        live.record.map(::resultOf).stateIn(viewModelScope, SharingStarted.Eagerly, resultOf(live.record.value))
    } else {
        completions.observeRecords()
            .map { records ->
                val matched = records.any {
                    it.puzzleRef.gameId == slot.gameId && it.puzzleRef.date == slot.day &&
                        it.time == key.timeMs && it.hints == key.hintsUsed
                }
                ResultUiState(isRecord = if (matched) true else null)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ResultUiState())
    }

    val primary: StateFlow<ResultPrimaryAction?> =
        if (calendar.today(slot.gameId) == slot.day) {
            progress().map { p ->
                p.nextUnsolved(after = slot.gameId)
                    ?.let { ResultPrimaryAction.Next(it, games.displayName(it)) }
                    ?: ResultPrimaryAction.AllDone
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        } else {
            MutableStateFlow(ResultPrimaryAction.Done)
        }

    fun retryRecordWrite() {
        live?.retryRecordWrite()
    }

    private fun resultOf(record: ActivePlay.Record) =
        ResultUiState(isRecord = record.badge, writeFailed = record.writeFailed, label = record.label)
}

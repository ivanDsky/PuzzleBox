package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.nextUnsolved
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.ResultKey
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

/**
 * The result route's live facts. [verdict] is the A10 verdict for THIS play — null while
 * the write is in flight/unknown, or when no live play holds this solve; [previousBestMs]
 * is the game's best before this play; [label] is null without a live play; [writeFailed]
 * (the retry affordance) is only ever true for a live play holding this solve.
 */
data class ResultUiState(
    val verdict: RecordVerdict? = null,
    val previousBestMs: Long? = null,
    val writeFailed: Boolean = false,
    val label: String? = null,
)

/**
 * The result route's state holder for [key]'s slot. The slot's live play
 * ([PlaySessionStore.peek]) speaks for this result only while it holds THIS solve — won,
 * with the key's time and hints: then the verdict, previous best, label and record-write
 * retry come from it. Once another stack replays or re-solves the slot, this entry keeps
 * the verdict it already saw and hides retry (A9, F2). Without a live play holding the
 * solve at entry (process death) the verdict is unknown (A15, N20): the session-start
 * baseline the verdict needs died with the play. A live play still names the label.
 *
 * [primary] (A7, SC10): on today's slot it is Next — [todaySet]'s next unsolved game after
 * this one that the [store] can play — or AllDone when none is left, and null until the set
 * first lands, so the screen never shows a Done that turns into Next. Any other day's
 * result (a replay) offers Done from the first frame. The set is collected only while the
 * screen is observed (plus a 5 s grace), so a day change is re-read.
 */
class ResultViewModel(
    key: ResultKey,
    store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    calendar: GameCalendar,
) : ViewModel() {

    private val slot = PlaySlot(GameId(key.gameId), LocalDate.parse(key.day))
    private val solve = ActivePlay.Solve(key.timeMs, key.hintsUsed)
    private val live: ActivePlay? = store.peek(slot)
    private val playable: Set<GameId> = store.playable.mapTo(HashSet()) { (game, _) -> game }

    val state: StateFlow<ResultUiState> = if (live != null) {
        val first = resultOf(ResultUiState(), live.record.value)
        live.record.runningFold(first, ::resultOf)
            .stateIn(viewModelScope, SharingStarted.Eagerly, first)
    } else {
        MutableStateFlow(ResultUiState())
    }

    val primary: StateFlow<ResultPrimaryAction?> =
        if (calendar.today(slot.gameId) == slot.day) {
            todaySet().map { entries ->
                val set = entries.filter { it.game in playable }
                set.nextUnsolved(after = slot.gameId)
                    ?.let { next -> ResultPrimaryAction.Next(next, set.first { it.game == next }.displayName) }
                    ?: ResultPrimaryAction.AllDone
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
        } else {
            MutableStateFlow(ResultPrimaryAction.Done)
        }

    fun retryRecordWrite() {
        live?.takeIf { it.record.value.solve == solve }?.retryRecordWrite()
    }

    /** This solve's facts while the play holds it; after a replay or another solve, the
     *  verdict this entry already saw stays (it was this solve's), and retry hides. */
    private fun resultOf(last: ResultUiState, record: ActivePlay.Record) =
        if (record.solve == solve) {
            ResultUiState(
                verdict = record.verdict,
                previousBestMs = record.previousBestMs,
                writeFailed = record.writeFailed,
                label = record.label,
            )
        } else {
            last.copy(writeFailed = false, label = record.label)
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

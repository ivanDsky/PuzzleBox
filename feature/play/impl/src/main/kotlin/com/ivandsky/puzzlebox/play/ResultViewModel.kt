package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.nextUnsolved
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleBoard
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The result route's live facts. [verdict] is the A10 verdict for THIS play — null while
 * the write is in flight/unknown, or when no live play holds this solve; [previousBest]
 * is the game's best before this play, ranked by [rankOrder]; [writeFailed] (the retry
 * affordance) is only ever true for a live play holding this solve.
 *
 * [game] names the game; [puzzleNumber] is the slot's edition number, and [practice] is
 * true once the history shows the slot has none (A22: it never ranks or counts).
 * [doneToday] of [total] is today's playable set solved, this game counted the moment it
 * is solved; [rolloverAt] is when the next puzzles land.
 */
data class ResultUiState(
    val verdict: RecordVerdict? = null,
    val previousBest: BestScore? = null,
    val rankOrder: RankOrder = RankOrder.TimeThenHints,
    val writeFailed: Boolean = false,
    val game: String = "",
    val puzzleNumber: Int? = null,
    val practice: Boolean = false,
    val doneToday: Int? = null,
    val total: Int? = null,
    val rolloverAt: Instant? = null,
)

/**
 * The result route's state holder for [key]'s slot. The slot's live play
 * ([PlaySessionStore.peek]) speaks for this result only while it holds THIS solve — won,
 * with the key's time and hints: then the verdict, previous best and record-write retry
 * come from it. Once another stack replays or re-solves the slot, this entry keeps the
 * verdict it already saw and hides retry (A9, F2). Without a live play holding the solve
 * at entry (process death) the verdict is unknown (A15, N20): the session-start baseline
 * the verdict needs died with the play.
 *
 * Everything else is read, so it holds after process death too: the edition number from
 * [history] (the same cached row the play's identity uses), the day's progress from
 * [todaySet] and the next rollover from [rollover]. [brand] is the game's registered brand.
 *
 * [primary] (A7, SC10): on today's slot it is Next — [todaySet]'s next unsolved game after
 * this one that the [store] can play — or AllDone when none is left, and null until the set
 * first lands, so the screen never shows a Done that turns into Next. Any other day's
 * result (a replay) offers Done from the first frame. The read flows run only while the
 * screen is observed (plus a 5 s grace), so a day change is re-read.
 *
 * [heroInsights], [heroRank] and [replay] feed the hero page.
 */
class ResultViewModel internal constructor(
    key: ResultKey,
    store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    calendar: GameCalendar,
    history: ObserveGameHistory,
    rollover: GetNextRollover,
    ui: GamePlayRegistry,
    games: GameRegistry,
    insights: ObserveResultInsights,
    rank: ObserveResultRank,
    private val board: FriendPuzzleBoard,
) : ViewModel() {

    private val slot = PlaySlot.Daily(GameId(key.gameId), LocalDate.parse(key.day))
    private val solve = ActivePlay.Solve(key.timeMs, key.hintsUsed)
    private val live: ActivePlay? = store.peek(slot)
    private val names: Map<GameId, String> = store.playable.toMap()
    private val today: Boolean = calendar.today(slot.gameId) == slot.day

    val brand: GameBrand = ui.brandOf(slot.gameId)

    /** This solve's record facts, folded eagerly so a later replay never erases them. */
    private val record: StateFlow<ResultUiState> = ResultUiState(game = games.displayName(slot.gameId)).let { blank ->
        if (live != null) {
            val first = resultOf(blank, live.record.value)
            live.record.runningFold(first, ::resultOf).stateIn(viewModelScope, SharingStarted.Eagerly, first)
        } else {
            MutableStateFlow(blank)
        }
    }

    private val playableToday: Flow<List<TodayEntry>> =
        todaySet().map { entries -> entries.filter { it.game in names } }

    val state: StateFlow<ResultUiState> = combine(
        record,
        history().map { days -> days.firstOrNull { it.gameId == slot.gameId && it.day == slot.day } },
        playableToday,
    ) { facts, day, set ->
        val practice = day != null && day.puzzleNumber == null
        facts.copy(
            puzzleNumber = day?.puzzleNumber,
            practice = practice,
            doneToday = set.count { it.status is TodayStatus.Solved || (today && it.game == slot.gameId) },
            total = set.size,
            rolloverAt = rollover(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), record.value)

    val primary: StateFlow<ResultPrimaryAction?> =
        if (today) {
            playableToday.map { set ->
                set.nextUnsolved(after = slot.gameId)
                    ?.let { next -> ResultPrimaryAction.Next(next, set.first { it.game == next }.displayName) }
                    ?: ResultPrimaryAction.AllDone
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
        } else {
            MutableStateFlow(ResultPrimaryAction.Done)
        }

    /** True for a past day's result: a replay counts nothing. */
    val replay: Boolean = !today

    /**
     * The hero page's streak, week and time spread; null until read, and for a practice puzzle.
     * Today's solve counts, a replay's does not.
     */
    val heroInsights: StateFlow<ResultInsights?> =
        insights(slot.gameId, slot.day, BestScore(key.timeMs, key.hintsUsed), counted = today)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The hero page's rank on today's board; hidden for a replay, which has no board to read. */
    val heroRank: StateFlow<ResultRank> =
        (if (today) rank(slot.gameId, slot.day) else flowOf(ResultRank.Hidden))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ResultRank.Hidden)

    /** The rank row's Google button: [activityContext] hosts Google's account picker, and is not kept. */
    fun signIn(activityContext: Context) {
        viewModelScope.launch { board.signIn(activityContext) }
    }

    fun retryRecordWrite() {
        live?.takeIf { it.record.value.solve == solve }?.retryRecordWrite()
    }

    /** This solve's facts while the play holds it; after a replay or another solve, the
     *  verdict this entry already saw stays (it was this solve's), and retry hides. */
    private fun resultOf(last: ResultUiState, record: ActivePlay.Record) =
        if (record.solve == solve) {
            last.copy(
                verdict = record.verdict,
                previousBest = record.previousBest,
                rankOrder = record.rankOrder,
                writeFailed = record.writeFailed,
            )
        } else {
            last.copy(writeFailed = false)
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

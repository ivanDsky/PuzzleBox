package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.SolveReplayRef
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.nextUnsolved
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.gameui.replay.ReplayUnavailable
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleBoard
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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
 * is solved; [rolloverAt] is when the next puzzles land. [replay] is Ready once this solve is stored with a replay,
 * Hidden before (a pending or failed write), or when there is none (A13).
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
    val replay: ReplaySectionState = ReplaySectionState.Hidden,
)

/**
 * The result route's state holder for [key]'s slot. The slot's live play
 * ([PlaySessionStore.peek]) speaks for this result only while it holds THIS solve — won,
 * with the key's time and hints: then the verdict, previous best and record-write retry
 * come from it. Once another stack plays again or re-solves the slot, this entry keeps the
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
 * result (a play again) offers Done from the first frame. The read flows run only while the
 * screen is observed (plus a 5 s grace), so a day change is re-read.
 *
 * [heroInsights], [heroRank] and [playAgain] feed the hero page.
 *
 * The replay is read from the stored completion, never from the live play: it shows once the solve is stored (the live
 * play's [ActivePlay.Record.stored], or after process death a stored row with this solve's time and hints), and never
 * while the write is pending or failed. A game with no replay board skips the load. [loadReplay] answers null for no
 * replay, so a stored solve without one shows [ReplaySectionState.Unavailable] instead of the row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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
    linkedIn: ObserveLinkedInDay?,
    private val board: FriendPuzzleBoard,
    private val completions: CompletionRepository,
    private val loadReplay: LoadSolveReplay,
) : ViewModel() {

    private val slot = PlaySlot.Daily(GameId(key.gameId), LocalDate.parse(key.day))
    private val solve = ActivePlay.Solve(key.timeMs, key.hintsUsed)
    private val live: ActivePlay? = store.peek(slot)
    private val liveHoldsSolve = live != null && live.record.value.solve == solve
    private val names: Map<GameId, String> = store.playable.toMap()
    private val today: Boolean = calendar.today(slot.gameId) == slot.day

    val brand: GameBrand = ui.brandOf(slot.gameId)

    /** The game's play UI, which owns its replay board; null for a game this build does not ship. */
    val playUi: GamePlayUi? = ui[slot.gameId]

    /** This solve's record facts, folded eagerly so a later play again never erases them. */
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

    private val dayRow = history().map { days -> days.firstOrNull { it.gameId == slot.gameId && it.day == slot.day } }

    /** Needs the edition number, which only the history row has, so it waits for that row. */
    private val replay: Flow<ReplaySectionState> = dayRow
        .map { day -> day?.let { games[slot.gameId]?.refOf?.invoke(PuzzleKey.Daily(slot.day, it.puzzleNumber)) } }
        .distinctUntilChanged()
        .flatMapLatest { ref -> if (ref == null || playUi?.replayBoard == null) flowOf(ReplaySectionState.Hidden) else replayOf(ref) }

    val state: StateFlow<ResultUiState> = combine(
        record,
        dayRow,
        playableToday,
        replay,
    ) { facts, day, set, replay ->
        val practice = day != null && day.puzzleNumber == null
        facts.copy(
            puzzleNumber = day?.puzzleNumber,
            practice = practice,
            doneToday = set.count { it.status is TodayStatus.Solved || (today && it.game == slot.gameId) },
            total = set.size,
            rolloverAt = rollover(),
            replay = replay,
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

    /** True for a past day's result: a play again counts nothing. */
    val playAgain: Boolean = !today

    /**
     * The hero page's streak, week and time spread; null until read, and for a practice puzzle.
     * Today's solve counts, a play again's does not.
     */
    val heroInsights: StateFlow<ResultInsights?> =
        insights(slot.gameId, slot.day, BestScore(key.timeMs, key.hintsUsed), counted = today)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * The hero page's rank on today's board; hidden for a play again, which has no board to read. With [linkedIn] on, the
     * day's connections already on the phone fold into it; the day is only observed here, never fetched.
     */
    val heroRank: StateFlow<ResultRank> =
        (if (today) rank(slot.gameId, slot.day, connections = linkedIn?.invoke(slot.gameId, slot.day)) else flowOf(ResultRank.Hidden))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ResultRank.Hidden)

    /** The rank row's Google button: [activityContext] hosts Google's account picker, and is not kept. */
    fun signIn(activityContext: Context) {
        viewModelScope.launch { board.signIn(activityContext) }
    }

    fun retryRecordWrite() {
        live?.takeIf { it.record.value.solve == solve }?.retryRecordWrite()
    }

    /**
     * True once this solve is stored. A live play that holds the solve says so itself ([ActivePlay.Record.stored]);
     * the time and hints match against the stored rows is only the cold case (no live play, after process death), where
     * no write signal exists. It must not decide the live case: an older row with the same time and hints would show
     * the row before the new write lands.
     */
    private fun storedOf(ref: PuzzleRef): Flow<Boolean> =
        if (liveHoldsSolve) {
            live!!.record.map { it.solve == solve && it.stored }.runningFold(false) { seen, now -> seen || now }
        } else {
            combine(completions.observeRecords(), completions.observePlayAgains(ref)) { records, again ->
                (records + again).any { it.puzzleRef == ref && it.time == solve.timeMs && it.hints == solve.hints }
            }
        }

    /** The row once [storedOf] says this solve is stored; the replay is read from that completion, never the live play. */
    private fun replayOf(ref: PuzzleRef): Flow<ReplaySectionState> =
        storedOf(ref)
            .distinctUntilChanged()
            .mapLatest { stored ->
                if (!stored) {
                    ReplaySectionState.Hidden
                } else {
                    loadReplay(SolveReplayRef.Latest(ref))?.let(ReplaySectionState::Ready) ?: unavailableOf(ref)
                }
            }

    /** A stored solve with no replay says so, naming a LinkedIn import. */
    private suspend fun unavailableOf(ref: PuzzleRef): ReplaySectionState {
        val stored = completions.observeRecords().first() + completions.observePlayAgains(ref).first()
        val source = stored.firstOrNull { it.puzzleRef == ref && it.time == solve.timeMs && it.hints == solve.hints }?.source
        return ReplaySectionState.Unavailable(source?.let(ReplayUnavailable::of) ?: ReplayUnavailable.NotRecorded)
    }

    /** This solve's facts while the play holds it; after a play again or another solve, the
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

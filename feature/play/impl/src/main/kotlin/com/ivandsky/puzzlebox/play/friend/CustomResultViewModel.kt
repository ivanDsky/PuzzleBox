package com.ivandsky.puzzlebox.play.friend

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.SolveReplayRef
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a friend's puzzle result draws: this solve's facts, the stored [puzzle], the solve's [standing] and the puzzle's [board]. */
internal data class CustomResultState(
    val timeMs: Long,
    val hintsUsed: Int,
    /** "Queens 8×8 · Crown the castle"; the game's name alone until the puzzle is read. */
    val title: String,
    val brand: GameBrand,
    val rankOrder: RankOrder,
    /** The friend's puzzle as stored when its code opened; null until read. */
    val puzzle: ReceivedPuzzle? = null,
    /** Null while the puzzle has no completion here yet: the first solve's write is in flight. */
    val standing: CustomStanding? = null,
    /** The puzzle's board; null until its first read lands. */
    val board: CustomBoardView? = null,
    val signIn: BoardSignIn = BoardSignIn.Idle,
    /** Ready once this solve is stored with a replay; Hidden while its write is pending or failed, or when there is none. */
    val replay: ReplaySectionState = ReplaySectionState.Hidden,
) {
    /** The author took the puzzle down: it still plays here, but its board takes no new entries. */
    val boardClosed: Boolean get() = puzzle?.unpublished == true
}

/** Whether a solve set this device's record of a friend's puzzle. */
internal sealed interface CustomStanding {
    /** The first solve here, or one that beat the record: it is the record. */
    data object Record : CustomStanding

    /** A later solve that left [record] standing. */
    data class PlayAgain(val record: BestScore) : CustomStanding
}

/**
 * One [CustomResultKey]'s result: the solve's own facts ride the key, so the page draws at
 * once and after process death; the stored puzzle and this device's completions of it follow.
 * The solve is the Record when it is the row that stands on the board — the record row, else
 * the oldest — and a play-again standing otherwise, naming that row's score. The game's name, size, brand
 * and rank order come from the registries; the feature names no game.
 *
 * The replay is read from the stored completion, never from the live play ([replayOf]): it shows once this solve is
 * stored, and never while the write is pending or failed. A game with no replay board skips the load.
 *
 * The puzzle's board comes from [FriendPuzzleBoard], so signing in from the page ([signIn])
 * fills it in place. The read reaches past the shown top rows, so this user's own row below
 * them is found.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CustomResultViewModel(
    private val key: CustomResultKey,
    completions: CompletionRepository,
    received: ReceivedPuzzleRepository,
    games: GameRegistry,
    plays: GamePlayRegistry,
    editors: GameEditorRegistry,
    private val store: PlaySessionStore,
    private val board: FriendPuzzleBoard,
    private val loadReplay: LoadSolveReplay,
) : ViewModel() {

    private val slot = PlaySlot.Custom(GameId(key.gameId), key.puzzleId)

    private val gameName = games.displayName(slot.gameId)

    private val solve = CustomResultState(
        timeMs = key.timeMs,
        hintsUsed = key.hintsUsed,
        title = gameName,
        brand = plays.brandOf(slot.gameId),
        rankOrder = games[slot.gameId]?.score?.rankOrder ?: RankOrder.TimeThenHints,
    )

    private val solved = ActivePlay.Solve(key.timeMs, key.hintsUsed)

    private val live: ActivePlay? = store.peek(slot)

    private val liveHoldsSolve = live != null && live.record.value.solve == solved

    /** The game's play UI, which owns its replay board; null for a game this build does not ship. */
    val playUi: GamePlayUi? = plays[slot.gameId]

    private val puzzleRef: PuzzleRef? = games[slot.gameId]?.refOf?.invoke(PuzzleKey.Custom(key.puzzleId))

    private val code = ShareCode.parse(key.puzzleId)

    private val puzzle: Flow<ReceivedPuzzle?> = code?.let(received::observe) ?: flowOf(null)

    private val records = completions.observeCustom(slot.gameId, key.puzzleId)

    val state: StateFlow<CustomResultState> = combine(puzzle, records, board(), board.signInStatus, replayOf(records)) { puzzle, records, board, signIn, replay ->
        solve.copy(
            title = puzzle?.let {
                val size = editors[it.gameId]?.sizeLabel(it.variant) ?: it.variant.value
                "$gameName $size · ${it.title}"
            } ?: gameName,
            puzzle = puzzle,
            standing = standingOf(records),
            board = board,
            signIn = signIn,
            replay = replay,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), solve)

    /**
     * Play again: resets the finished live play, if it is still held, before its board
     * reopens — a board reopened on a complete play would hand the old solve off again.
     */
    fun playAgain() {
        store.peek(slot)?.takeIf { it.controller.isComplete }?.playAgain()
    }

    /** The board's Google button: [activityContext] hosts Google's account picker, and is not kept. */
    fun signIn(activityContext: Context) {
        viewModelScope.launch { board.signIn(activityContext) }
    }

    /** The board once the puzzle is read; a re-read of the same puzzle (its post moving on) keeps the board's read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun board(): Flow<CustomBoardView?> = puzzle
        .distinctUntilChangedBy { it?.authorPin() }
        .flatMapLatest { puzzle -> puzzle?.let { board.observe(it, solve.rankOrder, BOARD_TOP, BOARD_READ) } ?: flowOf(null) }

    /**
     * Ready once [storedOf] says this solve is stored, read through [LoadSolveReplay] as the puzzle's newest solve. A live
     * play that holds the solve says so itself ([ActivePlay.Record.stored]): matching the rows by time and hints is only
     * the cold case (process death, no write signal) and must not decide the live one, where an older row with the same
     * time and hints would show the replay before the new write lands.
     */
    private fun replayOf(records: Flow<List<CompletionRecord>>): Flow<ReplaySectionState> {
        val ref = puzzleRef
        if (ref == null || playUi?.replayBoard == null) return flowOf(ReplaySectionState.Hidden)
        val stored = if (liveHoldsSolve) {
            live!!.record.map { it.solve == solved && it.stored }.runningFold(false) { seen, now -> seen || now }
        } else {
            records.map { rows -> rows.any { it.time == key.timeMs && it.hints == key.hintsUsed } }
        }
        return stored.distinctUntilChanged().mapLatest { isStored ->
            if (isStored) {
                loadReplay(SolveReplayRef.Latest(ref))?.let(ReplaySectionState::Ready) ?: ReplaySectionState.Hidden
            } else {
                ReplaySectionState.Hidden
            }
        }
    }

    /** [records] newest first; the row that stands is the record row, else the oldest. */
    private fun standingOf(records: List<CompletionRecord>): CustomStanding? {
        val stands = records.firstOrNull { it.isRecord } ?: records.lastOrNull() ?: return null
        return if (stands.time == key.timeMs && stands.hints == key.hintsUsed) {
            CustomStanding.Record
        } else {
            CustomStanding.PlayAgain(record = BestScore(stands.time, stands.hints))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Rows shown above the cut. */
        const val BOARD_TOP = 5

        /** Rows read: enough to find this user's own row well below the cut. */
        const val BOARD_READ = 50
    }
}

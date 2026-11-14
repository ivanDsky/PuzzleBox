package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.playTimeMs
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.BROKEN_PUZZLE_MESSAGE
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayFeedback
import com.ivandsky.puzzlebox.gameui.PlayGuide
import com.ivandsky.puzzlebox.gameui.PlayHints
import com.ivandsky.puzzlebox.gameui.PlayUndo
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuScoreModel
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuSessionCodec
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * Plays one [definition]'s board: adopt / restore / play-again and the clock. Every game decision
 * lives in the session and the [SudokuBoardState]; this class owns the process-local running
 * window and publishes every stored session as codec bytes (Queens parity).
 */
class SudokuPlayController(
    private val definition: SudokuDefinition,
    private val env: PlayEnvironment,
    private val highlights: SudokuHighlightSettings,
    private val now: () -> Instant = Instant::now,
    hintMode: HintMode = HintMode.Legacy,
) : GamePlayController, PlayClock, PlayHints, PlayUndo, PlayFeedback, PlayGuide {

    /** The hint path every board this controller builds runs (ticket 34). The caller picks it: Classic's registration passes no source, so it stays Legacy (A24). */
    internal val hintMode: HintMode = hintMode

    /** The two board tints; true until the player switches one off in the ⋮ menu. */
    var peerHighlight by mutableStateOf(true)
        private set

    var sameDigitHighlight by mutableStateOf(true)
        private set

    init {
        env.scope.launch { highlights.peer.collect { peerHighlight = it } }
        env.scope.launch { highlights.sameDigit.collect { sameDigitHighlight = it } }
    }

    // The local flag flips first so the board repaints now; the write only makes it stick. Launches on the
    // main-confined scope are FIFO, so the store sees the writes in tap order.
    fun choosePeerHighlight(enabled: Boolean) {
        peerHighlight = enabled
        env.scope.launch { highlights.setPeer(enabled) }
    }

    fun chooseSameDigitHighlight(enabled: Boolean) {
        sameDigitHighlight = enabled
        env.scope.launch { highlights.setSameDigit(enabled) }
    }

    /** The live interaction holder, null until a session is adopted. */
    var board by mutableStateOf<SudokuBoardState?>(null)
        private set

    private val session: SudokuSession? get() = board?.session

    override val isComplete: Boolean get() = session?.isComplete == true

    private val score = SudokuScoreModel()

    /** The open running window's start: null = clock stopped (not started, backgrounded, or won). */
    private var runningSince: Instant? by mutableStateOf(null)

    var nowMs by mutableStateOf(0L)
        private set

    //region adopt / restore

    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt {
        if (puzzle.gameId != definition.id) return Adopt.Rejected(BROKEN_PUZZLE_MESSAGE)
        val decoded = when (val result = definition.decodePuzzle(puzzle.payload)) {
            is DecodeResult.Ok -> result.value
            is DecodeResult.Err -> return Adopt.Rejected(BROKEN_PUZZLE_MESSAGE)
        }
        val sudokuRef = ref as? SudokuPuzzleRef ?: SudokuPuzzleRef(ref.gameId, ref.key)
        start(SudokuSession(ref = sudokuRef, puzzle = decoded))
        return Adopt.Adopted
    }

    /** A restored completed session shows its result without re-emitting the win. */
    override fun adoptRestored(bytes: ByteArray): Restored? =
        when (val restored = SudokuSessionCodec.deserialize(bytes)) {
            is DecodeResult.Ok -> {
                val s = restored.value
                runningSince = null
                board = boardFor(s)
                // The running clock is process-local: a restored game in progress reopens it now.
                if (!s.isComplete) openWindow()
                Restored(s.ref)
            }
            is DecodeResult.Err -> null
        }

    override fun playAgain() {
        val current = session ?: return
        start(SudokuSession(ref = current.ref, puzzle = current.puzzle))
    }

    /** Binds [fresh] and opens its clock window: the clock runs from the moment the puzzle opens. */
    private fun start(fresh: SudokuSession) {
        runningSince = null
        board = boardFor(fresh)
        // Published before the first move so a process death right after adopt still restores.
        publish(fresh)
        openWindow()
    }

    /** A fresh holder per board; the old one's search in flight is dropped first, so it can neither show nor save over the new session. */
    private fun boardFor(session: SudokuSession): SudokuBoardState {
        board?.stepHints?.reset()
        return SudokuBoardState(
            initialSession = session,
            score = score,
            onWinRecord = env.onWon,
            now = now,
            log = env.moveLog,
            runningSince = { runningSince },
            onSessionChanged = ::publish,
            settle = ::settle,
            hintMode = hintMode,
            scope = env.scope,
            peerHighlight = { peerHighlight },
            sameDigitHighlight = { sameDigitHighlight },
        )
    }

    private fun publish(s: SudokuSession) {
        env.onSessionChanged(SudokuSessionCodec.serialize(s))
    }

    /** Lessons have one open cell and no rule to hold back, so guiding changes nothing yet. */
    override fun guide() = Unit

    //endregion

    //region PlayClock

    /** A win folds the running window in; the window itself is opened by adopt/restore/play-again/foreground. */
    private fun settle(next: SudokuSession): SudokuSession =
        if (next.isComplete) foldRunning(next) else next

    private fun openWindow() {
        val start = now()
        nowMs = start.toEpochMilli()
        runningSince = start
    }

    private fun foldRunning(target: SudokuSession): SudokuSession {
        val rs = runningSince ?: return target
        runningSince = null
        return target.copy(elapsedMs = playTimeMs(target.elapsedMs, rs, now()))
    }

    override fun tick() {
        nowMs = now().toEpochMilli()
    }

    override fun onBackground() {
        val rs = runningSince ?: return
        runningSince = null
        board?.bankElapsed(playTimeMs(0L, rs, now()))
    }

    override fun onForeground() {
        val current = session ?: return
        if (runningSince == null && !current.isComplete) openWindow()
    }

    override val clockWindow: Any? get() = runningSince

    override fun displayedElapsedMs(): Long {
        val banked = session?.elapsedMs ?: return 0L
        return playTimeMs(banked, runningSince, Instant.ofEpochMilli(nowMs))
    }

    //endregion

    //region PlayHints / PlayUndo / PlayFeedback

    override val hintsUsed: Int get() = session?.hintsUsed ?: 0

    override val hintEnabled: Boolean get() = session?.isComplete == false

    override val hintCaption: String? get() = board?.hintCaption

    override fun requestHint() {
        board?.requestHint()
    }

    override val canUndo: Boolean get() = board?.canUndo ?: false

    override val canRedo: Boolean get() = board?.canRedo ?: false

    override fun undo() {
        board?.undo()
    }

    override fun redo() {
        board?.redo()
    }

    override val violation: String? get() = board?.violation

    override val rejection: String? get() = board?.rejection

    //endregion
}

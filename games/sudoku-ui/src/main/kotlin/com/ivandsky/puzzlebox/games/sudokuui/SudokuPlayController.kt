package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
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
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuScoreModel
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuSessionCodec
import java.time.Duration
import java.time.Instant

/**
 * Plays one [definition]'s board: adopt / restore / replay and the clock. Every game decision
 * lives in the session and the [SudokuBoardState]; this class owns the process-local running
 * window and publishes every stored session as codec bytes (Queens parity).
 */
class SudokuPlayController(
    private val definition: SudokuDefinition,
    private val env: PlayEnvironment,
    private val now: () -> Instant = Instant::now,
) : GamePlayController, PlayClock, PlayHints, PlayUndo, PlayFeedback, PlayGuide {

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
                if (!s.isComplete && s.hasProgress()) openWindow()
                Restored(s.ref)
            }
            is DecodeResult.Err -> null
        }

    override fun replay() {
        val current = session ?: return
        start(SudokuSession(ref = current.ref, puzzle = current.puzzle))
    }

    private fun start(fresh: SudokuSession) {
        runningSince = null
        board = boardFor(fresh)
        // Published before the first move so a process death right after adopt still restores.
        publish(fresh)
    }

    private fun boardFor(session: SudokuSession) = SudokuBoardState(
        initialSession = session,
        score = score,
        onWinRecord = env.onWon,
        now = now,
        onSessionChanged = ::publish,
        settle = ::settle,
    )

    private fun publish(s: SudokuSession) {
        env.onSessionChanged(SudokuSessionCodec.serialize(s))
    }

    /** Lessons have one open cell and no rule to hold back, so guiding changes nothing yet. */
    override fun guide() = Unit

    //endregion

    //region PlayClock

    /** Opens the window on the first committed command or counted hint; a win folds it in. */
    private fun settle(next: SudokuSession): SudokuSession {
        if (next.isComplete) return foldRunning(next)
        if (runningSince == null) openWindow()
        return next
    }

    private fun openWindow() {
        val start = now()
        nowMs = start.toEpochMilli()
        runningSince = start
    }

    private fun foldRunning(target: SudokuSession): SudokuSession {
        val rs = runningSince ?: return target
        runningSince = null
        return target.copy(elapsedMs = target.elapsedMs + Duration.between(rs, now()).toMillis())
    }

    override fun tick() {
        nowMs = now().toEpochMilli()
    }

    override fun onBackground() {
        val rs = runningSince ?: return
        runningSince = null
        board?.bankElapsed(Duration.between(rs, now()).toMillis())
    }

    override fun onForeground() {
        val current = session ?: return
        if (runningSince == null && !current.isComplete && current.hasProgress()) openWindow()
    }

    override val clockWindow: Any? get() = runningSince

    override fun displayedElapsedMs(): Long {
        val banked = session?.elapsedMs ?: return 0L
        val window = runningSince?.let { Duration.between(it, Instant.ofEpochMilli(nowMs)).toMillis() } ?: 0L
        return (banked + window).coerceAtLeast(0L)
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

    // Undoing every move empties the history but keeps the banked time, so that counts too.
    private fun SudokuSession.hasProgress(): Boolean = hasStarted || elapsedMs > 0
}

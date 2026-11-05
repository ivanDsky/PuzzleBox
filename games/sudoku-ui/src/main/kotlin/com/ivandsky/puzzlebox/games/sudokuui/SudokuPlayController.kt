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
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuScoreModel
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuSessionCodec
import java.time.Duration
import java.time.Instant

/**
 * Plays one [definition]'s board: adopt / restore / replay, the clock, select + place, undo,
 * and publishing every committed session. The session decides every rule; this class only
 * routes input and owns the process-local running window (Queens parity).
 */
class SudokuPlayController(
    private val definition: SudokuDefinition,
    private val env: PlayEnvironment,
    private val now: () -> Instant = Instant::now,
) : GamePlayController, PlayClock, PlayHints, PlayUndo, PlayFeedback, PlayGuide {

    var session by mutableStateOf<SudokuSession?>(null)
        private set

    var selected by mutableStateOf<Int?>(null)
        private set

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
        val sudokuRef = ref as? SudokuPuzzleRef ?: SudokuPuzzleRef(ref.gameId, ref.date, ref.puzzleNumber)
        start(SudokuSession(ref = sudokuRef, puzzle = decoded))
        return Adopt.Adopted
    }

    /** A restored completed session shows its result without re-emitting the win. */
    override fun adoptRestored(bytes: ByteArray): Restored? =
        when (val restored = SudokuSessionCodec.deserialize(bytes)) {
            is DecodeResult.Ok -> {
                val s = restored.value
                runningSince = null
                selected = null
                session = s
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
        selected = null
        session = fresh
        // Published before the first move so a process death right after adopt still restores.
        publish(fresh)
    }

    /** Lessons have one open cell and no rule to hold back, so guiding changes nothing yet. */
    override fun guide() = Unit

    //endregion

    //region input

    fun select(idx: Int) {
        selected = idx
    }

    fun place(digit: Int) {
        val current = session ?: return
        val idx = selected ?: return
        val command = SudokuMoves.place(current.board, idx, digit) ?: return
        commit(current.withCommand(command, now()))
    }

    private fun commit(next: SudokuSession) {
        val previous = session
        val settled = if (next.isComplete) {
            foldRunning(next)
        } else {
            if (runningSince == null) openWindow()
            next
        }
        session = settled
        publish(settled)
        if (previous?.isComplete == false && settled.isComplete) score.completion(settled)?.let(env.onWon)
    }

    private fun publish(s: SudokuSession) {
        env.onSessionChanged(SudokuSessionCodec.serialize(s))
    }

    //endregion

    //region PlayClock

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
        val current = session ?: return
        val rs = runningSince ?: return
        runningSince = null
        session = current.copy(elapsedMs = current.elapsedMs + Duration.between(rs, now()).toMillis())
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

    // The hint ladder lands with the interaction layer (ticket 16); until then the button stays off.
    override val hintEnabled: Boolean get() = false

    override val hintCaption: String? get() = null

    override fun requestHint() = Unit

    override val canUndo: Boolean get() = session?.let { !it.isComplete && it.history.canUndo } ?: false

    override val canRedo: Boolean get() = session?.let { !it.isComplete && it.history.canRedo } ?: false

    override fun undo() {
        val current = session?.takeIf { !it.isComplete } ?: return
        val step = current.history.undo(current.board) ?: return
        commit(current.copy(board = step.state, history = step.history))
    }

    override fun redo() {
        val current = session?.takeIf { !it.isComplete } ?: return
        val step = current.history.redo(current.board) ?: return
        commit(current.copy(board = step.state, history = step.history))
    }

    override val violation: String? get() = null

    override val rejection: String? get() = null

    //endregion

    /** The restored history is empty (codec v1), so progress also counts banked time and entries. */
    private fun SudokuSession.hasProgress(): Boolean =
        history.canUndo || elapsedMs > 0 || hintsUsed > 0 || board.cells.any { it is SudokuCell.Entry || it is SudokuCell.Notes }
}

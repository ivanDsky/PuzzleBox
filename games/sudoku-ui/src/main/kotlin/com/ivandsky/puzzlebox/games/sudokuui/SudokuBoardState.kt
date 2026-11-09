package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.StepHints
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHints
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuSpotlightMapper
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuStepGame
import java.time.Instant
import kotlinx.coroutines.CoroutineScope

/**
 * Selection, input routing and the staged hint over one [SudokuSession]; the session decides
 * every rule. [selected], [hint] and [rejection] live in memory only, so a reopened board starts
 * with none of them while the session (notes mode included) comes back whole. A solved board
 * holds no selection: the solving move clears it and [select] refuses one.
 *
 * Every player action clears the hint and the rejection. A hint target counts into
 * [SudokuSession.hintsUsed] the first time it is presented; a repeat request on a Place or
 * Reveal target is stage 2 and commits the digit as an undoable HintReveal.
 *
 * Clock: [settle] is the owner's hook on every committed command or counted hint, applied
 * before the session is stored and the win record built, so the record carries the real time.
 * Selection and the Notes toggle never reach it.
 *
 * [HintMode.Stepwise] with a [scope] (HintParity, Mini only) swaps the staged hint for [StepHints]:
 * a press shows a [spotlight] and never places (A1), a culprit's second press erases it as one
 * undo unit (A4), and every input above clears the Spotlight in the same call (A10). [hint] then
 * stays null, so the legacy wash, rings and caption never draw (A15).
 */
class SudokuBoardState(
    initialSession: SudokuSession,
    private val hints: SudokuHints = SudokuHints(),
    private val score: ScoreModel<SudokuSession>? = null,
    private val onWinRecord: (CompletionRecord) -> Unit = {},
    private val now: () -> Instant = Instant::now,
    private val onSessionChanged: (SudokuSession) -> Unit = {},
    private val settle: (SudokuSession) -> SudokuSession = { it },
    hintMode: HintMode = HintMode.Legacy,
    scope: CoroutineScope? = null,
) {

    var session by mutableStateOf(initialSession)
        private set

    var selected by mutableStateOf<Int?>(null)
        private set

    /** The presented hint; null when none is showing. */
    var hint by mutableStateOf<SudokuHint?>(null)
        private set

    /** Stage 2: the presented hint's digit has been placed on the board. */
    var hintRevealed by mutableStateOf(false)
        private set

    /** The last refused input's copy, shown until the next action. */
    var rejection by mutableStateOf<String?>(null)
        private set

    private var winEmitted = false

    // Survives the hint being cleared, so looking away and asking again does not count twice.
    private val countedTargets = HashSet<Pair<Class<out SudokuHint>, List<Int>>>()

    private val stepHints: StepHints<SudokuStep, SudokuSpotlightMapper.Key>? =
        (hintMode as? HintMode.Stepwise)?.let { mode ->
            scope?.let {
                val game = SudokuStepGame(session = { session }, onEraseCulprit = ::eraseMistake)
                StepHints(mode, it, game, used = { session.hintsUsed }, onCount = ::storeHintsUsed)
            }
        }

    /** The Stepwise hint on screen; null in Legacy mode and after any input. */
    val spotlight: Spotlight? get() = stepHints?.spotlight

    val canUndo: Boolean get() = session.canUndo

    val canRedo: Boolean get() = session.canRedo

    // While a Spotlight shows its callout carries the text, so the caption slot stays empty (A11).
    val hintCaption: String? get() = if (spotlight != null) null else SudokuHintCaptions.hint(hint, session.board)

    val violation: String? get() = if (spotlight != null) null else SudokuHintCaptions.violation(session.board)

    /** The cell the hint places into, null for hints that point at a set of cells. */
    val hintTarget: Int?
        get() = when (val h = hint) {
            is SudokuHint.Place -> h.cell
            is SudokuHint.Reveal -> h.cell
            else -> null
        }

    /** The cells that justify or make up the hint, the target excluded. */
    val hintCells: Set<Int>
        get() {
            val geometry = session.board.geometry
            return when (val h = hint) {
                is SudokuHint.Place -> {
                    // A naked single is justified by everything the cell sees, not by one unit.
                    val cells = h.unit?.let(geometry::cellsOf) ?: geometry.peersOf(h.cell)
                    cells.filterTo(HashSet()) { it != h.cell }
                }
                is SudokuHint.Conflict -> h.conflict.cells.toSet()
                is SudokuHint.Wrong -> h.cells.toSet()
                is SudokuHint.Reveal, SudokuHint.None, null -> emptySet()
            }
        }

    /** The cells whose candidates the hint's eliminations cleared, stage 1 only (spec A8). */
    val hintStruck: Set<Int>
        get() = (hint as? SudokuHint.Place)?.takeUnless { hintRevealed }?.struck?.toSet().orEmpty()

    val model: SudokuBoardModel get() = sudokuBoardModel(session, selected, hintTarget, hintCells, hintStruck)

    //region input

    fun select(idx: Int) {
        if (session.isComplete || idx !in 0 until session.board.geometry.cellCount) return
        clearFeedback()
        selected = idx
    }

    /** A note in notes mode, otherwise an entry; refused on a Given. */
    fun input(digit: Int) {
        clearFeedback()
        val idx = selected ?: return
        if (rejectsGiven(idx)) return
        val board = session.board
        val command = if (session.notesMode) SudokuMoves.toggleNote(board, idx, digit) else SudokuMoves.place(board, idx, digit)
        command?.let(::record)
    }

    /** Removes the selected cell's value if it has one, otherwise its notes. */
    fun erase() {
        clearFeedback()
        val idx = selected ?: return
        if (rejectsGiven(idx)) return
        SudokuMoves.erase(session.board, idx)?.let(::record)
    }

    fun clear() {
        clearFeedback()
        SudokuMoves.clear(session.board)?.let(::record)
    }

    fun undo() {
        clearFeedback()
        commit(session.withUndo())
    }

    fun redo() {
        clearFeedback()
        commit(session.withRedo())
    }

    /** A setting, not a move: no undo unit and no clock. */
    fun toggleNotesMode() {
        clearFeedback()
        store(session.withNotesMode(!session.notesMode))
    }

    //endregion

    //region hints

    fun requestHint() {
        if (session.isComplete) return
        rejection = null
        stepHints?.let {
            it.press()
            return
        }
        val active = hint
        if (!hintRevealed && (active is SudokuHint.Place || active is SudokuHint.Reveal)) {
            reveal(active)
            return
        }
        val next = hints.hint(session.puzzle, session.board)
        if (next == SudokuHint.None) {
            clearFeedback()
            return
        }
        hint = next
        hintRevealed = false
        if (countedTargets.add(targetOf(next))) store(settle(session.withHintUsed()))
    }

    private fun reveal(active: SudokuHint) {
        val (cell, digit) = when (active) {
            is SudokuHint.Place -> active.cell to active.digit
            is SudokuHint.Reveal -> active.cell to active.digit
            else -> return
        }
        val command = SudokuMoves.reveal(session.board, cell, digit) ?: return
        record(command)
        hintRevealed = true
    }

    /** The culprit's second press (A4): one undoable Erase; a cell with nothing to erase records nothing. */
    private fun eraseMistake(step: SudokuStep.Mistake) {
        SudokuMoves.erase(session.board, step.cell)?.let(::record)
    }

    /** A newly counted Stepwise hint: through [settle] like the legacy count, never [commit], so the Spotlight stays. */
    private fun storeHintsUsed(used: Int) {
        store(settle(session.copy(hintsUsed = used)))
    }

    private fun targetOf(hint: SudokuHint): Pair<Class<out SudokuHint>, List<Int>> {
        val cells = when (hint) {
            is SudokuHint.Place -> listOf(hint.cell)
            is SudokuHint.Reveal -> listOf(hint.cell)
            is SudokuHint.Conflict -> hint.conflict.cells
            is SudokuHint.Wrong -> hint.cells
            SudokuHint.None -> emptyList()
        }
        return hint.javaClass to cells
    }

    //endregion

    /** Folds [deltaMs] of finished running time into the banked clock. */
    fun bankElapsed(deltaMs: Long) {
        if (deltaMs <= 0L) return
        store(session.copy(elapsedMs = session.elapsedMs + deltaMs))
    }

    private fun rejectsGiven(idx: Int): Boolean {
        if (session.board.cells[idx] !is SudokuCell.Given) return false
        rejection = SudokuHintCaptions.GIVEN_REJECTION
        return true
    }

    private fun clearFeedback() {
        hint = null
        hintRevealed = false
        rejection = null
        stepHints?.inputMade()
    }

    private fun record(command: SudokuCommand) = commit(session.withCommand(command, now()))

    private fun commit(next: SudokuSession) {
        // A no-op (finished game, empty undo stack) records nothing and leaves the clock alone.
        if (next == session) return
        val previous = session
        val settled = settle(next)
        store(settled)
        if (!previous.isComplete && settled.isComplete) {
            selected = null
            if (!winEmitted) {
                winEmitted = true
                score?.completion(settled)?.let(onWinRecord)
            }
        }
    }

    private fun store(next: SudokuSession) {
        if (next == session) return
        session = next
        onSessionChanged(next)
    }
}

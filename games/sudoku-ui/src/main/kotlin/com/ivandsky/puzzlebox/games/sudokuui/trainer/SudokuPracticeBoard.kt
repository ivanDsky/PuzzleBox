package com.ivandsky.puzzlebox.games.sudokuui.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPractice
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPracticeJudgement
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPracticeJudgement.Reason
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import com.ivandsky.puzzlebox.games.sudokuui.SudokuBoardState
import com.ivandsky.puzzlebox.games.sudokuui.SudokuHintCaptions
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuSpotlightMapper
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A Mini Sudoku practice board (spec §2.2): the real play board ([state], the daily's own gestures) over a drill puzzle
 * from [start], every open cell showing its candidates as notes. [SudokuPractice] judges each move against the easiest
 * rung of the hint ladder, and a move that is not Right is undone at once.
 *
 * The judge is the play state's own `settle` hook: it runs on a settled move before the session is stored, and handing
 * back the session from before the move leaves that exact session (board, history, no redo) in place. A Sudoku move
 * is one gesture (a square, then a digit or a note), so there is no tap cycle to wait out. Nothing here is saved: no
 * completion, no replay, no stored play.
 *
 * Counting (spec §2.2): a deduction that changes several notes is Right square by square. [onTrainedTurn] hears
 * `Right` once, on its first square; the squares still to come are held in [pending] and answered without a report or
 * a new Spotlight, even when the first strike has made an easier step appear in the meantime. An undone move or Show me
 * reports only when the easiest rung is the trained [technique].
 *
 * The status Spotlight ([shown]) is drawn over the grid by [SudokuTrainer.Board] until the player's next input.
 */
internal class SudokuPracticeBoard(
    private val puzzle: SudokuPuzzle,
    start: SudokuBoard,
    override val technique: TechniqueId,
    private val onTrainedTurn: (TrainerVerdict) -> Unit,
    private val now: () -> Instant = Instant::now,
) : PracticeBoard {

    /** A step the player has started: the notes it still strikes, and the status its first square answered with. */
    private class Pending(val remaining: MutableSet<Pair<Int, Int>>, val status: PracticeStatus.Right)

    private val current = MutableStateFlow<PracticeStatus>(PracticeStatus.FindNextMove)
    override val status: StateFlow<PracticeStatus> = current.asStateFlow()

    /** The Spotlight the grid draws now: a judged move's or Show me's, until the player's next input clears it. */
    var shown by mutableStateOf<Spotlight?>(null)
        private set

    private val pending = ArrayList<Pending>()
    private var spotlights = 0

    internal val state = SudokuBoardState(
        initialSession = SudokuSession(ref = null, puzzle = puzzle, board = start),
        now = now,
        settle = ::judge,
        onInput = { shown = null },
    )

    override fun showMe() {
        val session = state.session
        val show = SudokuPractice.showMe(puzzle, session.board) ?: return
        val spotlight = spotlight(show.step, session.board)
        val next = SudokuPractice.commands(session.board, show.step).fold(session) { acc, command -> acc.withCommand(command, now()) }
        state.adopt(next)
        pending.clear()
        shown = spotlight
        if (show.rung == technique) onTrainedTurn(TrainerVerdict.ShowMe)
        current.value = if (next.isComplete) PracticeStatus.Solved else PracticeStatus.Shown(spotlight)
    }

    /** The verdict on the move [next] makes: [next] when it is Right, the session from before it otherwise. */
    private fun judge(next: SudokuSession): SudokuSession {
        val before = state.session
        val move = SudokuTrainerMove.between(before.board, next.board)
        pending.firstOrNull { it.continues(move) }?.let { return continuing(it, move, next) }
        return when (val judged = SudokuPractice.judge(puzzle, before.board, move)) {
            is SudokuPracticeJudgement.Right -> right(judged, before, move, next)
            is SudokuPracticeJudgement.Easier -> {
                val spotlight = spotlight(judged.step, before.board)
                undone(judged.rung, PracticeStatus.Easier(SudokuTrainerCatalog.name(judged.rung), spotlight), before, spotlight)
            }
            is SudokuPracticeJudgement.Wrong -> undone(judged.rung, PracticeStatus.Wrong(why(judged.reason, move, next.board)), before, null)
        }
    }

    /** The first square of its step (or a single's only one): its Spotlight plays and a trained step is reported once. */
    private fun right(judged: SudokuPracticeJudgement.Right, before: SudokuSession, move: SudokuTrainerMove, next: SudokuSession): SudokuSession {
        val trained = judged.rung == technique
        val spotlight = spotlight(judged.step, before.board)
        val answer = PracticeStatus.Right(spotlight, trained)
        val rest = (judged.step as? SudokuStep.Elimination)?.let { squares(it) }.orEmpty() - listOfNotNull(square(move)).toSet()
        if (rest.isNotEmpty()) pending += Pending(rest.toMutableSet(), answer)
        if (trained) onTrainedTurn(TrainerVerdict.Right)
        shown = spotlight
        current.value = if (next.isComplete) PracticeStatus.Solved else answer
        return next
    }

    /** A later square of a started step: Right as before, with no new report and no new Spotlight. */
    private fun continuing(step: Pending, move: SudokuTrainerMove, next: SudokuSession): SudokuSession {
        step.remaining -= square(move)!!
        if (step.remaining.isEmpty()) pending -= step
        current.value = if (next.isComplete) PracticeStatus.Solved else step.status
        return next
    }

    /** [status] for a move that is not Right: the move is dropped, and the trained technique's turn it was is a Wrong. */
    private fun undone(rung: TechniqueId?, status: PracticeStatus, before: SudokuSession, spotlight: Spotlight?): SudokuSession {
        if (rung == technique) onTrainedTurn(TrainerVerdict.Wrong)
        shown = spotlight
        current.value = status
        return before
    }

    private fun spotlight(step: SudokuStep, board: SudokuBoard): Spotlight =
        SudokuSpotlightMapper.map(step, puzzle, board, playId = ++spotlights)

    private fun Pending.continues(move: SudokuTrainerMove): Boolean = square(move) in remaining

    /** The note a move strikes, the only kind of move a step goes on past its first square. */
    private fun square(move: SudokuTrainerMove): Pair<Int, Int>? =
        (move as? SudokuTrainerMove.RemoveNote)?.let { it.idx to it.digit }

    private fun squares(step: SudokuStep.Elimination): Set<Pair<Int, Int>> =
        step.struck.flatMap { (cell, mask) -> (1..puzzle.geometry.size).filter { mask and (1 shl (it - 1)) != 0 }.map { cell to it } }.toSet()

    /** One plain line for a move that broke the solution or was no move. [after] is the board with the move made. */
    private fun why(reason: Reason, move: SudokuTrainerMove, after: SudokuBoard): String = when (reason) {
        Reason.WrongDigit -> {
            val idx = (move as SudokuTrainerMove.Place).idx
            SudokuHintCaptions.violation(after) ?: SudokuHintCaptions.hint(SudokuHint.Wrong(listOf(idx)), after) ?: "That digit doesn't fit here."
        }
        Reason.SolutionNote -> {
            val remove = move as SudokuTrainerMove.RemoveNote
            val geometry = puzzle.geometry
            "That ${remove.digit} can still go in row ${geometry.rowOf(remove.idx) + 1}, column ${geometry.colOf(remove.idx) + 1}, so its note stays."
        }
        Reason.NotAMove -> "Place a digit or take a note off a square."
    }
}

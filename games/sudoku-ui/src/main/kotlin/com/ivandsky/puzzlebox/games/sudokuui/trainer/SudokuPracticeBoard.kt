package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.trainer.BoardKind
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
 * Counting (spec §2.2): a deduction that changes several notes is made square by square. Each square but the last is
 * [PracticeStatus.KeepGoing] with the squares left, held in [step], even when the first strike has made an easier step
 * appear in the meantime; the last is [PracticeStatus.Right], and [onTrainedTurn] hears `Right` once, then. An undone
 * move or Show me reports only when the easiest rung is the trained [technique].
 *
 * An Easier move or Show me stands as an explanation until [gotIt]: the board takes no move meanwhile, whatever
 * the keypad sends it. The status Spotlight is drawn over the grid by [SudokuTrainer.Board] from [status].
 *
 * A step that ends the board (spec §2.2, [BoardKind]: solved, or the easiest rung is out of this kind's part) is decided
 * when the step completes, never between the squares of a notes step. A Right move that ends it reports
 * [PracticeStatus.Done] with that step's Spotlight; if Show me made the ending step, [PracticeStatus.Shown] stands and
 * [gotIt] turns it into `Done(null)`. A Done board refuses moves and Show me, like an explanation.
 */
internal class SudokuPracticeBoard(
    private val puzzle: SudokuPuzzle,
    start: SudokuBoard,
    override val technique: TechniqueId,
    private val kind: BoardKind,
    private val onTrainedTurn: (TrainerVerdict) -> Unit,
    private val now: () -> Instant = Instant::now,
) : PracticeBoard {

    /** A step the player has started: the notes it still strikes, whether its rung is the trained one, and its Spotlight. */
    private class Step(val remaining: MutableSet<Pair<Int, Int>>, val trained: Boolean, val spotlight: Spotlight)

    private val current = MutableStateFlow<PracticeStatus>(
        if (SudokuPractice.isDone(puzzle, start, technique, kind)) PracticeStatus.Done(null) else PracticeStatus.FindNextMove,
    )
    override val status: StateFlow<PracticeStatus> = current.asStateFlow()

    private var step: Step? = null
    private var spotlights = 0

    /** Whether the step Show me made, whose explanation stands, ended the board: [gotIt] then reads Done. */
    private var shownEnds = false

    internal val state = SudokuBoardState(
        initialSession = SudokuSession(ref = null, puzzle = puzzle, board = start),
        now = now,
        settle = ::judge,
    )

    /** Whether an [PracticeStatus.Easier] or [PracticeStatus.Shown] explanation stands, which takes no move. */
    private val explaining: Boolean get() = current.value.let { it is PracticeStatus.Easier || it is PracticeStatus.Shown }

    override fun showMe() {
        if (explaining || current.value is PracticeStatus.Done) return
        val session = state.session
        val show = SudokuPractice.showMe(puzzle, session.board) ?: return
        val spotlight = spotlight(show.step, session.board)
        val next = SudokuPractice.commands(session.board, show.step).fold(session) { acc, command -> acc.withCommand(command, now()) }
        state.adopt(next)
        step = null
        if (show.rung == technique) onTrainedTurn(TrainerVerdict.ShowMe)
        shownEnds = over(next)
        current.value = PracticeStatus.Shown(spotlight)
    }

    override fun gotIt() {
        if (!explaining) return
        current.value = if (shownEnds) PracticeStatus.Done(null) else PracticeStatus.FindNextMove
        shownEnds = false
    }

    /**
     * The verdict on the move [next] makes: [next] when it is Right, the session from before it otherwise. While an
     * explanation stands every move is refused, unjudged and unreported.
     */
    private fun judge(next: SudokuSession): SudokuSession {
        val before = state.session
        if (explaining || current.value is PracticeStatus.Done) return before
        val move = SudokuTrainerMove.between(before.board, next.board)
        step?.takeIf { square(move) in it.remaining }?.let { return continuing(it, move, next) }
        return when (val judged = SudokuPractice.judge(puzzle, before.board, move)) {
            is SudokuPracticeJudgement.Right -> right(judged, before, move, next)
            is SudokuPracticeJudgement.Easier -> {
                val spotlight = spotlight(judged.step, before.board)
                undone(judged.rung, PracticeStatus.Easier(SudokuTrainerCatalog.name(judged.rung), spotlight), before)
            }
            is SudokuPracticeJudgement.Wrong -> undone(judged.rung, PracticeStatus.Wrong(why(judged.reason, move, next.board)), before)
        }
    }

    /** The first square of its step (or a single's only one): Right when it finishes the step, KeepGoing when squares remain. */
    private fun right(judged: SudokuPracticeJudgement.Right, before: SudokuSession, move: SudokuTrainerMove, next: SudokuSession): SudokuSession {
        val trained = judged.rung == technique
        val spotlight = spotlight(judged.step, before.board)
        val rest = (judged.step as? SudokuStep.Elimination)?.let { squares(it) }.orEmpty() - listOfNotNull(square(move)).toSet()
        step = null
        if (rest.isEmpty()) {
            finished(trained, spotlight, next)
        } else {
            step = Step(rest.toMutableSet(), trained, spotlight)
            current.value = PracticeStatus.KeepGoing(rest.size, trained)
        }
        return next
    }

    /** A later square of a started step: KeepGoing while squares remain, Right on the last. */
    private fun continuing(started: Step, move: SudokuTrainerMove, next: SudokuSession): SudokuSession {
        started.remaining -= square(move)!!
        if (started.remaining.isEmpty()) {
            step = null
            finished(started.trained, started.spotlight, next)
        } else {
            current.value = PracticeStatus.KeepGoing(started.remaining.size, started.trained)
        }
        return next
    }

    /** A step is done: a trained one is reported once, and the band reads Right, or Done when the step ended the board. */
    private fun finished(trained: Boolean, spotlight: Spotlight, next: SudokuSession) {
        if (trained) onTrainedTurn(TrainerVerdict.Right)
        current.value = if (over(next)) PracticeStatus.Done(spotlight) else PracticeStatus.Right(spotlight, trained)
    }

    /** Whether [session] leaves this board nothing to ask: solved, or the easiest rung is out of this [kind]'s part. */
    private fun over(session: SudokuSession): Boolean =
        session.isComplete || SudokuPractice.isDone(puzzle, session.board, technique, kind)

    /** [status] for a move that is not Right: the move is dropped, and the trained technique's turn it was is a Wrong. */
    private fun undone(rung: TechniqueId?, status: PracticeStatus, before: SudokuSession): SudokuSession {
        if (rung == technique) onTrainedTurn(TrainerVerdict.Wrong)
        current.value = status
        return before
    }

    private fun spotlight(step: SudokuStep, board: SudokuBoard): Spotlight =
        SudokuSpotlightMapper.map(step, puzzle, board, playId = ++spotlights)

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

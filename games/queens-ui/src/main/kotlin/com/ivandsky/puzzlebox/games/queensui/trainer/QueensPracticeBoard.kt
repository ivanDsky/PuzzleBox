package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensPractice
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensPracticeMove
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensPracticeVerdict
import com.ivandsky.puzzlebox.games.queensui.QueensBoardState
import com.ivandsky.puzzlebox.games.queensui.QueensMoveJudge
import com.ivandsky.puzzlebox.games.queensui.hint.QueensSpotlightMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One Queens practice board (spec §2.2): the play board's own [state], its gestures and its Auto-cross, played from
 * [start] until it is done ([PracticeStatus.Done], as [kind] says). Each move is judged by [QueensPractice] against the easiest rung with a deduction on the board
 * the move was made on, and any move that is not Right is undone at once, the board back as it was. The judging runs
 * on the move's own call (a look-ahead rung takes up to 90 ms), so the undo is never seen late.
 *
 * The board writes nothing: no recorder, no score, no saved session, so nothing of a practice reaches a record.
 *
 * **Tap cycles.** A tap on an empty square is a cross, and the player may tap it again for a queen. The cross is
 * therefore held, drawn but not judged, until the cycle settles: [SETTLE_MS] pass without a touch on it, or the player
 * touches another square or starts a drag. Then the cross is judged as a move on the board it was made on, and undone
 * if it is not Right. A second tap on the held square is the queen, judged on its own at once against the board from
 * before the cross; refused, it puts that board back, cross and all. A tap on a cross already kept (or one a queen
 * forces) is a queen too, judged at once.
 *
 * **Drag lines.** Each square a drag paints is a cross judged on its own, at once. The first one that is not Right
 * is undone, and the rest of that drag paints nothing, so a wrong line is never half-kept.
 *
 * **Steps.** A deduction that crosses several squares is one step: the squares the player crosses after the first
 * one that was judged are part of it, are not judged again, and are not counted again. Each square but the last
 * reports [PracticeStatus.KeepGoing] with how many are left; [PracticeStatus.Right] comes with the last one.
 *
 * **Explanations.** While [PracticeStatus.Easier] or [PracticeStatus.Shown] stands the board takes no move (each is
 * put back, unjudged) and Show me does nothing, until [gotIt]. The same holds once the board is [PracticeStatus.Done]:
 * the move that ends it is judged and kept, and then the board is closed. When Show me makes the step that ends the
 * board, the status is [PracticeStatus.Shown] first, and [gotIt] closes the board.
 *
 * [onTrainedTurn] gets each trained turn once: Right when a step of the trained technique is finished, Wrong
 * for each undone move made while it was the easiest rung, ShowMe for Show me.
 */
internal class QueensPracticeBoard(
    private val puzzle: QueensPuzzle,
    override val technique: TechniqueId,
    private val kind: BoardKind,
    start: QueensBoard,
    compute: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val onTrainedTurn: (TrainerVerdict) -> Unit,
) : PracticeBoard, QueensMoveJudge {

    private val mutableStatus = MutableStateFlow<PracticeStatus>(PracticeStatus.FindNextMove)
    override val status: StateFlow<PracticeStatus> = mutableStatus.asStateFlow()

    private var held: Held? = null
    private var running: Running? = null
    private var drag = false
    private var halted = false
    private var showing = false
    private var playId = 0

    /** The real play board's state: the board is drawn, tapped and dragged as in play. */
    val state = QueensBoardState(unplayedSession(puzzle, start), mode = HintMode.Stepwise(compute), scope = scope)

    init {
        state.judge = this
        if (QueensPractice.isDone(puzzle, state.session.board, technique, kind)) mutableStatus.value = PracticeStatus.Done(null)
    }

    /** A cross waiting for its tap cycle to settle: the session from before it, and the pause that settles it. */
    private class Held(val before: QueensSession, val cell: Cell) {
        var timer: Job? = null
    }

    /** The deduction the player is partway through crossing: the squares it still crosses, and what it showed. */
    private data class Running(val spotlight: Spotlight, val trained: Boolean, val remaining: Set<Cell>)

    /** An easier-move or Show me explanation stands: the board takes no move until [gotIt]. */
    private val explaining: Boolean
        get() = mutableStatus.value.let { it is PracticeStatus.Easier || it is PracticeStatus.Shown }

    /** The board takes no move and Show me does nothing: an explanation stands, or the board is done. */
    private val closed: Boolean
        get() = explaining || mutableStatus.value is PracticeStatus.Done

    override fun input(idx: Int, drag: Boolean) {
        this.drag = drag
        halted = false
        val waiting = held ?: return
        if (drag || idx != waiting.cell.idx) settleHeld()
    }

    override fun judged(before: QueensSession, after: QueensSession, move: QueensReplayMove): QueensSession = when {
        showing -> after
        closed -> before
        move is QueensReplayMove.Cross -> crossed(before, after, move.cell)
        move is QueensReplayMove.Place -> placed(before, after, move.cell)
        move is QueensReplayMove.Hint || move is QueensReplayMove.AutoCross -> after
        else -> {
            // Taking a queen or a cross back, wiping, undoing, redoing: no verdict, and the step in hand is over.
            dropHeld()
            running = null
            after
        }
    }

    private fun crossed(before: QueensSession, after: QueensSession, cell: Cell): QueensSession {
        if (halted) return before
        if (drag) {
            if (accepts(before, after, QueensPracticeMove.Cross(cell))) return after
            halted = true
            return before
        }
        val waiting = Held(before, cell)
        waiting.timer = scope.launch {
            delay(SETTLE_MS)
            settleHeld()
        }
        held = waiting
        return after
    }

    private fun placed(before: QueensSession, after: QueensSession, cell: Cell): QueensSession {
        // A queen on the held cross closes the cycle: it is judged against the board from before the cross.
        val origin = held?.takeIf { it.cell == cell }?.before ?: before
        dropHeld()
        if (!accepts(origin, after, QueensPracticeMove.Place(cell))) return origin
        return after
    }

    private fun settleHeld() {
        val waiting = held ?: return
        dropHeld()
        if (!accepts(waiting.before, state.session, QueensPracticeMove.Cross(waiting.cell))) state.restore(waiting.before)
    }

    private fun dropHeld() {
        held?.timer?.cancel()
        held = null
    }

    /**
     * A square of a right step was made, leaving [after]; [left] are the squares the step still crosses. The step is
     * Right, and counted, only when none are: and then, if [after] ends the board, the status is Done with the step
     * in hand, not Right.
     */
    private fun progressed(spotlight: Spotlight, trained: Boolean, left: Set<Cell>, after: QueensSession) {
        if (left.isNotEmpty()) {
            running = Running(spotlight, trained, left)
            mutableStatus.value = PracticeStatus.KeepGoing(left.size, trained)
            return
        }
        running = null
        if (trained) onTrainedTurn(TrainerVerdict.Right)
        mutableStatus.value =
            if (after.isComplete || QueensPractice.isDone(puzzle, after.board, technique, kind)) PracticeStatus.Done(spotlight)
            else PracticeStatus.Right(spotlight, trained)
    }

    /** Judges [move] made on [before], which left [after], and reports it; true when the move stays. */
    private fun accepts(before: QueensSession, after: QueensSession, move: QueensPracticeMove): Boolean {
        val partway = running
        if (move is QueensPracticeMove.Cross && partway != null && move.cell in partway.remaining) {
            progressed(partway.spotlight, partway.trained, partway.remaining - move.cell, after)
            return true
        }
        return when (val verdict = QueensPractice.judge(puzzle, before.board, move)) {
            // No rung has a deduction here: nothing to judge by, and nothing to count.
            null -> true
            is QueensPracticeVerdict.Right -> {
                progressed(spotlightOf(verdict.step), verdict.rung.trainerId == technique, verdict.remaining, after)
                true
            }
            is QueensPracticeVerdict.Easier -> {
                undone(verdict.rung)
                mutableStatus.value = PracticeStatus.Easier(QueensPracticeCopy.technique(verdict.rung), spotlightOf(verdict.step))
                false
            }
            is QueensPracticeVerdict.Wrong -> {
                undone(verdict.rung)
                mutableStatus.value = PracticeStatus.Wrong(QueensPracticeCopy.why(verdict.why))
                false
            }
        }
    }

    private fun undone(rung: QueensRung) {
        if (rung.trainerId == technique) onTrainedTurn(TrainerVerdict.Wrong)
    }

    override fun gotIt() {
        mutableStatus.value = when (mutableStatus.value) {
            is PracticeStatus.Easier -> PracticeStatus.FindNextMove
            is PracticeStatus.Shown ->
                if (QueensPractice.isDone(puzzle, state.session.board, technique, kind)) PracticeStatus.Done(null) else PracticeStatus.FindNextMove
            else -> return
        }
    }

    override fun showMe() {
        if (closed) return
        settleHeld()
        if (mutableStatus.value is PracticeStatus.Done) return // the held cross was the step that ended the board
        val board = state.session.takeUnless { it.isComplete }?.board ?: return
        val deduction = QueensPractice.show(puzzle, board) ?: return
        running = null
        showing = true
        when (val result = deduction.result) {
            is QueensStep.Deduction.Result.Cross ->
                state.crossHintCells(QueensPractice.crossings(puzzle, board, deduction).map { it.idx })
            is QueensStep.Deduction.Result.LastCell -> state.placeHintQueen(result.cell.idx)
        }
        showing = false
        if (deduction.unit.rung.trainerId == technique) onTrainedTurn(TrainerVerdict.ShowMe)
        mutableStatus.value = PracticeStatus.Shown(spotlightOf(deduction))
    }

    private fun spotlightOf(step: QueensStep.Deduction): Spotlight = QueensSpotlightMapper.map(step, puzzle, ++playId)

    private companion object {
        /** How long a tap on an empty square waits for a second tap before it is judged as a cross. */
        const val SETTLE_MS = 600L
    }
}

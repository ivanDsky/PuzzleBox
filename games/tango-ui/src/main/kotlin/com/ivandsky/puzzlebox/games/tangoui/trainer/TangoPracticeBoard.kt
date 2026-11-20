package com.ivandsky.puzzlebox.games.tangoui.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPractice
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPracticeStep
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPracticeVerdict
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayController
import com.ivandsky.puzzlebox.games.tangoui.TangoSemantics
import com.ivandsky.puzzlebox.games.tangoui.hint.TangoSpotlightMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Tango practice board (spec §2.2): a drill puzzle on the daily's own [TangoPlayController], played to the end with
 * the daily's own gestures. Each move is judged by [TangoPractice] against the easiest hint-ladder rung at that
 * moment; a move that is not Right is undone at once, to the exact session from before it
 * ([TangoPlayController.revertTo]), so no cross, mark, mistake or redo is left behind.
 *
 * The board writes nothing: the controller's environment drops every session save and win, and the session carries no
 * puzzle reference, so no completion, saved play, daily or replay can follow from it.
 *
 * **Tap cycle.** A tap cycles an empty square empty → sun → moon → empty, so a sun on the way to a moon must not be
 * judged. A tap therefore opens a cycle on its square and the judgement is held until the cycle settles: [SETTLE_MILLIS]
 * after the last tap on that square (every further tap re-arms the wait), or at once on a tap on another square, or
 * when the tap completes the board. The net change is what is judged: a square cycled back to empty is restored to the
 * session from before the cycle with no verdict. A filled square is final: a tap on it, on a given, or on a finished
 * board does nothing. TalkBack's placement actions name their end state, so they are judged at once, with no cycle.
 *
 * **A step of several squares** (a full line, an equal pair) is Right square by square and reports
 * [TrainerVerdict.Right] once, on its first square, when its rung is the trained technique. The board remembers the
 * squares still to play and keeps judging them as that step's own, whatever the first squares changed on the board;
 * a Right move on another step replaces it. Show me finishes a step in progress (its turn was already reported, so
 * nothing is reported again), else makes the first whole step of the easiest rung and reports
 * [TrainerVerdict.ShowMe] when that rung is the trained technique.
 *
 * Every report goes to [onTrainedTurn] only for a turn whose easiest rung was the trained technique: Right once per
 * step, [TrainerVerdict.Wrong] for a move that was undone. An undone move on another rung reports nothing.
 *
 * Input and judging run on the caller's thread (the main one): a 6×6 board's rungs are cheap. [scope] times the cycle.
 */
internal class TangoPracticeBoard(
    private val puzzle: TangoPuzzle,
    private val solution: List<TangoCell>,
    override val technique: TechniqueId,
    startGrid: List<TangoCell?>,
    private val scope: CoroutineScope,
    compute: CoroutineDispatcher,
    private val onTrainedTurn: (TrainerVerdict) -> Unit,
) : PracticeBoard {

    /** The daily's controller, the board's one source of truth. Taps and placements reach it only through the judge. */
    internal val controller = TangoPlayController(
        env = PlayEnvironment(scope, onSessionChanged = {}, onWon = {}),
        hintMode = HintMode.Stepwise(compute),
    ).also { controller ->
        val placements = startGrid.withIndex()
            .filter { (idx, value) -> value != null && !puzzle.isGiven(idx) }
            .associate { (idx, value) -> idx to checkNotNull(value) }
        controller.revertTo(TangoSession(ref = null, puzzle = puzzle, placements = placements))
    }

    private val state = MutableStateFlow<PracticeStatus>(PracticeStatus.FindNextMove)

    override val status: StateFlow<PracticeStatus> = state.asStateFlow()

    /**
     * What the board overlays: the Spotlight of the last move's status, until the next input (as the daily hint's
     * Spotlight goes at the next input). Compose state, so the board redraws when it changes.
     */
    var spotlight: Spotlight? by mutableStateOf(null)
        private set

    private var playId = 0
    private var lastMove: Int? = null

    /** The tap cycle on one square: the session from before its first tap, and the wait that judges it. */
    private class Cycle(val idx: Int, val base: TangoSession) {
        var wait: Job? = null
    }

    /** A Right step with squares still to play. */
    private class InProgress(
        val step: TangoPracticeStep,
        val spotlight: Spotlight,
        val trained: Boolean,
        val remaining: MutableSet<Int>,
    )

    private var cycle: Cycle? = null
    private var inProgress: InProgress? = null

    /** A tap on the square [idx]: a step of its cycle, judged once the cycle settles. */
    fun onTap(idx: Int) {
        spotlight = null
        if (cycle?.idx != idx) settle()
        val session = controller.session ?: return
        if (session.isComplete || puzzle.isGiven(idx)) return
        val open = cycle ?: if (session.grid()[idx] == null) Cycle(idx, session).also { cycle = it } else return
        controller.onTap(idx)
        if (controller.isComplete) {
            settle()
        } else {
            open.wait?.cancel()
            open.wait = scope.launch {
                delay(SETTLE_MILLIS)
                settle()
            }
        }
    }

    /** A TalkBack action naming [placement]'s end state: judged at once, with no cycle. */
    fun onPlacement(placement: TangoPlacement) {
        spotlight = null
        settle()
        val session = controller.session ?: return
        val value = placement.value
        if (session.isComplete || value == null || puzzle.isGiven(placement.idx) || session.grid()[placement.idx] != null) return
        controller.onPlacement(placement)
        judge(session, placement.idx, value)
    }

    override fun showMe() {
        settle()
        val session = controller.session ?: return
        if (session.isComplete) return
        val grid = session.grid()
        val progress = inProgress
        inProgress = null
        val shown: Spotlight
        if (progress != null) {
            shown = progress.spotlight
            progress.step.squares.filter { it.idx in progress.remaining }.forEach { place(it.idx, it.value) }
        } else {
            val step = TangoPractice.show(puzzle, solution, grid, lastMove) ?: return
            shown = spotlightOf(step.squares.first().shown, grid)
            step.squares.forEach { place(it.idx, it.value) }
            if (step.rung?.trainerId == technique) onTrainedTurn(TrainerVerdict.ShowMe)
        }
        spotlight = shown
        publish(PracticeStatus.Shown(shown))
    }

    private fun place(idx: Int, value: TangoCell) {
        controller.onPlacement(TangoPlacement(idx, value))
        lastMove = idx
    }

    /** Judges the open cycle, if any, now. */
    private fun settle() {
        val open = cycle ?: return
        cycle = null
        open.wait?.cancel()
        val value = controller.session?.grid()?.get(open.idx)
        if (value == null) controller.revertTo(open.base) else judge(open.base, open.idx, value)
    }

    /** [value] now stands on [idx] of the board that was [base]: keep it when it is Right, else undo it and say why. */
    private fun judge(base: TangoSession, idx: Int, value: TangoCell) {
        val before = base.grid()
        inProgress?.takeIf { idx in it.remaining && it.step.contains(idx, value) }?.let { progress ->
            progress.remaining -= idx
            if (progress.remaining.isEmpty()) inProgress = null
            lastMove = idx
            publish(PracticeStatus.Right(progress.spotlight, progress.trained))
            return
        }
        when (val verdict = TangoPractice.judge(puzzle, solution, before, technique, idx, value)) {
            is TangoPracticeVerdict.Right -> {
                val shown = spotlightOf(checkNotNull(verdict.step.squareAt(idx)).shown, before)
                val remaining = verdict.step.squares.map { it.idx }.filter { it != idx }.toMutableSet()
                inProgress = remaining.takeIf { it.isNotEmpty() }?.let { InProgress(verdict.step, shown, verdict.trained, it) }
                lastMove = idx
                if (verdict.trained) onTrainedTurn(TrainerVerdict.Right)
                spotlight = shown
                publish(PracticeStatus.Right(shown, verdict.trained))
            }
            is TangoPracticeVerdict.Easier -> {
                controller.revertTo(base)
                if (verdict.rung.trainerId == technique) onTrainedTurn(TrainerVerdict.Wrong)
                val shown = spotlightOf(verdict.step.squares.first().shown, before)
                spotlight = shown
                publish(PracticeStatus.Easier(TangoTrainerCatalog.name(verdict.rung.trainerId), shown))
            }
            is TangoPracticeVerdict.Wrong -> {
                val why = whyWrong(before, idx, value)
                controller.revertTo(base)
                if (verdict.rung?.trainerId == technique) onTrainedTurn(TrainerVerdict.Wrong)
                publish(PracticeStatus.Wrong(why))
            }
        }
    }

    /** The game's own violation line for the move when it breaks a rule outright, else a plain one. */
    private fun whyWrong(before: List<TangoCell?>, idx: Int, value: TangoCell): String {
        val after = before.toMutableList().also { it[idx] = value }
        return TangoSemantics.firstViolationPhrase(TangoRendererModel.of(TangoState(puzzle, after))) ?: DOES_NOT_MATCH
    }

    private fun spotlightOf(step: TangoStep, grid: List<TangoCell?>): Spotlight =
        TangoSpotlightMapper.map(step, puzzle, grid, playId = ++playId)

    /** [next] unless the board is solved, which has no Spotlight and the last word. */
    private fun publish(next: PracticeStatus) {
        if (controller.isComplete) {
            spotlight = null
            state.value = PracticeStatus.Solved
        } else {
            state.value = next
        }
    }

    private companion object {
        /** The same wait the daily's own settle debounce uses before a new violation shows. */
        const val SETTLE_MILLIS = MotionTokens.SettleMillis

        const val DOES_NOT_MATCH = "That square doesn't match the solution."
    }
}

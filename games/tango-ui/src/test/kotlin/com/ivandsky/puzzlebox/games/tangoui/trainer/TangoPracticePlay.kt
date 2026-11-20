package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPractice
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPracticeVerdict
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/**
 * One practice board under test, on virtual time, with every turn it reported to the trainer. Moves are found by asking
 * the engine's judge about the board in front of the player, so no test hard-codes a cell.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TangoPracticePlay private constructor(
    private val scope: TestScope,
    val puzzle: Puzzle,
    val technique: TechniqueId,
    val board: TangoPracticeBoard,
    val turns: MutableList<TrainerVerdict>,
) {

    /** A move the player could make on the board now, and what the engine's judge says of it. */
    data class Move(val idx: Int, val value: TangoCell, val verdict: TangoPracticeVerdict) {

        /** Right, and part of a deduction of the trained technique. */
        val isTrainedRight: Boolean get() = (verdict as? TangoPracticeVerdict.Right)?.trained == true

        /** How many squares the step this move belongs to has (1 for a move that is not Right). */
        val stepSquares: Int get() = (verdict as? TangoPracticeVerdict.Right)?.step?.squares?.size ?: 1

        /** The easiest rung the judge measured the move against; null when no rung fires. */
        val rung: TangoStepRung?
            get() = when (verdict) {
                is TangoPracticeVerdict.Right -> verdict.step.rung
                is TangoPracticeVerdict.Easier -> verdict.rung
                is TangoPracticeVerdict.Wrong -> verdict.rung
            }
    }

    val decoded: TangoPuzzle = (TangoCodec.decode(puzzle.payload) as DecodeResult.Ok).value
    val solution: List<TangoCell> = checkNotNull(TangoUniqueness.solve(decoded))

    val session: TangoSession get() = checkNotNull(board.controller.session)
    val grid: List<TangoCell?> get() = session.grid()
    val status: PracticeStatus get() = board.status.value

    /** The rung the board's next Show me would play; null when no rung fires. */
    val easiestRung: TangoStepRung? get() = TangoPractice.show(decoded, solution, grid, null)?.rung

    val emptySquares: List<Int> get() = grid.indices.filter { grid[it] == null }

    /** Every move of either kind on every empty square, with the judge's verdict on the board as it is now. */
    fun moves(): List<Move> {
        val now = grid
        return emptySquares.flatMap { idx ->
            TangoCell.entries.map { value -> Move(idx, value, TangoPractice.judge(decoded, solution, now, technique, idx, value)) }
        }
    }

    /** Taps [idx] until it shows [value]: one tap for a sun, two for a moon (the cycle is empty, sun, moon). */
    fun tap(idx: Int, value: TangoCell) = repeat(if (value == TangoCell.SUN) 1 else 2) { board.onTap(idx) }

    fun pause(millis: Long) {
        scope.advanceTimeBy(millis)
        scope.runCurrent()
    }

    /** Waits out the tap cycle's pause. */
    fun settle() = pause(SETTLE_MILLIS + 1)

    /** Taps [value] onto [idx] and waits for the judgement. */
    fun play(idx: Int, value: TangoCell) {
        tap(idx, value)
        settle()
    }

    /** Plays [move] and waits for the judgement. */
    fun play(move: Move) = play(move.idx, move.value)

    /** Show me, step after step, until [done] holds; false when the board fills first. */
    fun showUntil(done: (TangoPracticePlay) -> Boolean): Boolean {
        while (!done(this)) {
            if (emptySquares.isEmpty()) return false
            board.showMe()
            board.gotIt()
        }
        return true
    }

    companion object {

        /** The pause the board waits out after a tap before it judges (a cycle's last tap). */
        const val SETTLE_MILLIS = 500L

        /** A board on [puzzle] for [technique], or null when the trainer offers none. */
        fun open(scope: TestScope, puzzle: Puzzle, technique: TechniqueId): TangoPracticePlay? {
            val turns = mutableListOf<TrainerVerdict>()
            val board = TangoTrainerFixtures.trainer.practice(puzzle, technique, scope, turns::add) as TangoPracticeBoard?
            return board?.let { TangoPracticePlay(scope, puzzle, technique, it, turns) }
        }

        /** A fresh board for every technique of every daily the trainer has a start board for. */
        fun all(scope: TestScope): List<TangoPracticePlay> = TangoTrainerFixtures.dailies.flatMap { puzzle ->
            TangoTrainerFixtures.trainer.catalog().mapNotNull { open(scope, puzzle, it.id) }
        }
    }
}

/** The first board with a move [match] accepts, and that move; fails naming [what] when no start board has one. */
internal fun List<TangoPracticePlay>.firstWith(what: String, match: (TangoPracticePlay.Move) -> Boolean) =
    firstNotNullOfOrNull { play -> play.moves().firstOrNull(match)?.let { play to it } }
        ?: throw AssertionError("no start board has $what")

/**
 * Like [firstWith], but a board may be played on with Show me until it has such a move (its easiest rung then changes
 * with the board). The turns the walk reported are forgotten.
 */
internal fun List<TangoPracticePlay>.firstWithAfterShowing(what: String, match: (TangoPracticePlay, TangoPracticePlay.Move) -> Boolean): Pair<TangoPracticePlay, TangoPracticePlay.Move> {
    for (play in this) {
        var found: TangoPracticePlay.Move? = null
        if (play.showUntil { p -> p.moves().firstOrNull { move -> match(p, move) }.also { found = it } != null }) {
            play.turns.clear()
            return play to checkNotNull(found)
        }
    }
    throw AssertionError("no board reaches $what")
}

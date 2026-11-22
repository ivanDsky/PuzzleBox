package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPractice
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPracticeVerdict
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayFixture
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Tango practice board as a player meets it (spec §2.2): where it starts, which moves it keeps, which it undoes and
 * says why, and what it reports to the trainer. Moves are read off the engine's judge on the live board, so no test
 * names a cell. The tap cycle, Show me and the finished board are in [TangoPracticeBoardPlayTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TangoPracticeBoardTest {

    private val sign = TechniqueId("sign")
    private val canonical = TangoTrainerFixtures.canonical

    @Test
    fun `a board opens where its kind opens, with a move of its technique to make`() = runTest {
        for (kind in BoardKind.entries) {
            val boards = TangoPracticePlay.all(this, kind)
            assertTrue(boards.isNotEmpty())
            for (play in boards) {
                val label = "${play.technique.value} $kind"
                assertEquals(label, TangoPractice.start(play.decoded, play.solution, play.technique, kind), play.grid)
                assertEquals(label, PracticeStatus.FindNextMove, play.status)
                assertTrue(label, play.moves().any { it.isTrainedRight })
                assertEquals(label, emptyList<TrainerVerdict>(), play.turns)
                assertFalse(label, play.session.history.canUndo)
                assertEquals(label, 0, play.session.mistakeCount)
                assertNull(label, play.session.ref)
                assertFalse(label, play.session.isComplete)
            }
        }
    }

    @Test
    fun `no board for a technique that is never the easiest, another game, a broken or ambiguous puzzle, or an unknown id`() = runTest {
        BoardKind.entries.forEach { kind ->
            fun board(puzzle: Puzzle, technique: TechniqueId) =
                TangoTrainerFixtures.trainer.practice(puzzle, technique, kind, this, onTrainedTurn = {})

            assertNull(board(canonical, TechniqueId("split-pair")))
            assertNull(board(canonical, TechniqueId("no-such-technique")))
            assertNull(board(Puzzle(GameId("other-game"), canonical.variant, canonical.formatVersion, canonical.payload), sign))
            assertNull(board(Puzzle(canonical.gameId, canonical.variant, canonical.formatVersion, byteArrayOf(1, 2, 3)), sign))
            assertNull(board(TangoPlayFixture.envelope(TangoPlayFixture.ambiguous), sign))
        }
    }

    @Test
    fun `every move of every instance of the easiest rung is kept, and counts once its step is finished`() = runTest {
        var severalInstances = 0
        for (start in TangoPracticePlay.all(this)) {
            val rights = start.moves().filter { it.isTrainedRight }
            if (rights.map { (it.verdict as TangoPracticeVerdict.Right).step }.distinct().size > 1) severalInstances++
            for (move in rights) {
                val play = checkNotNull(TangoPracticePlay.open(this, start.puzzle, start.technique, start.kind))
                val label = "${start.technique.value} ${move.idx}=${move.value}"
                play.play(move)
                assertEquals(label, move.value, play.grid[move.idx])
                if (move.stepSquares == 1) {
                    assertTrue(label, play.status.isTrainedRightOrDone())
                    assertEquals(label, listOf(TrainerVerdict.Right), play.turns)
                } else {
                    assertEquals(label, PracticeStatus.KeepGoing(move.stepSquares - 1, trained = true), play.status)
                    assertEquals(label, emptyList<TrainerVerdict>(), play.turns)
                }
            }
        }
        assertTrue("a start board with several instances of its rung", severalInstances > 0)
    }

    @Test
    fun `a step of several squares keeps going with the squares left, then is Right and counts once on its last`() = runTest {
        val (play, first) = TangoPracticePlay.all(this).firstWith("a step of several squares") { it.isTrainedRight && it.stepSquares > 1 }
        val step = (first.verdict as TangoPracticeVerdict.Right).step
        val others = step.squares.filter { it.idx != first.idx }

        play.play(first)
        assertEquals(PracticeStatus.KeepGoing(left = others.size, trained = true), play.status)

        others.forEachIndexed { done, square ->
            assertEquals(emptyList<TrainerVerdict>(), play.turns)
            play.play(square.idx, square.value)
            assertEquals(square.value, play.grid[square.idx])
            val left = others.size - done - 1
            if (left > 0) {
                assertEquals(PracticeStatus.KeepGoing(left, trained = true), play.status)
            } else {
                assertTrue(play.status.isTrainedRightOrDone())
            }
        }
        assertEquals(listOf(TrainerVerdict.Right), play.turns)
    }

    @Test
    fun `an easier move is undone to the exact board, says which technique is easier, and counts as a miss`() = runTest {
        val (play, easier) = TangoPracticePlay.all(this).firstWith("an easier move") { it.verdict is TangoPracticeVerdict.Easier }
        val right = play.moves().first { it.isTrainedRight }
        val before = play.session

        play.play(easier)

        assertEquals(before, play.session)
        val status = play.status as PracticeStatus.Easier
        assertEquals(TangoTrainerCatalog.name(checkNotNull(easier.rung).trainerId), status.technique)
        assertEquals(listOf(TrainerVerdict.Wrong), play.turns)

        play.board.gotIt()
        play.play(right)
        assertEquals(right.value, play.grid[right.idx])
        assertEquals(
            listOf(TrainerVerdict.Wrong) + List(if (right.stepSquares == 1) 1 else 0) { TrainerVerdict.Right },
            play.turns,
        )
    }

    @Test
    fun `while an easier move's explanation stands the board takes no move, Show me does nothing, and Got it releases it`() = runTest {
        val (play, easier) = TangoPracticePlay.all(this).firstWith("an easier move") { it.verdict is TangoPracticeVerdict.Easier }
        val right = play.moves().first { it.isTrainedRight }
        play.play(easier)
        val explanation = play.status
        val before = play.session
        val turns = play.turns.toList()

        play.play(right)
        play.board.onPlacement(TangoPlacement(right.idx, right.value))
        play.board.showMe()
        play.settle()

        assertEquals(before, play.session)
        assertEquals(explanation, play.status)
        assertEquals(turns, play.turns)

        play.board.gotIt()

        assertEquals(PracticeStatus.FindNextMove, play.status)
        assertEquals(before, play.session)
        play.play(right)
        assertEquals(right.value, play.grid[right.idx])
    }

    @Test
    fun `Got it does nothing when no explanation stands`() = runTest {
        val (play, right) = TangoPracticePlay.all(this).firstWith("a Right move") { it.isTrainedRight }
        play.board.gotIt()
        assertEquals(PracticeStatus.FindNextMove, play.status)

        play.play(right)
        val status = play.status
        play.board.gotIt()

        assertEquals(status, play.status)
    }

    @Test
    fun `a wrong move is undone to the exact board, whatever its value, and counts as a miss`() = runTest {
        var wrongMoves = 0
        for (play in TangoPracticePlay.all(this)) {
            val before = play.session
            val wrong = play.moves().filter { it.verdict is TangoPracticeVerdict.Wrong }
            for (move in wrong) {
                play.play(move)
                val label = "${play.technique.value} ${move.idx}=${move.value}"
                assertEquals(label, before, play.session)
                assertFalse(label, play.board.controller.canUndo || play.board.controller.canRedo)
                assertNull(label, play.board.controller.lastRejection)
                val why = (play.status as? PracticeStatus.Wrong)?.why
                assertTrue(label, why?.isNotBlank() == true)
                // The move is undone, so the line says what it would have done, never "These two" or "Row 2 has 4".
                assertTrue("$label: $why", why!!.startsWith("That "))
            }
            assertEquals(play.technique.value, List(wrong.size) { TrainerVerdict.Wrong }, play.turns)
            wrongMoves += wrong.size
        }
        assertTrue(wrongMoves > 0)
    }

    @Test
    fun `a wrong move on another rung is undone and reports nothing`() = runTest {
        val (play, wrong) = TangoPracticePlay.all(this, BoardKind.Review).firstWithAfterShowing("a wrong move on another rung") { board, move ->
            move.verdict is TangoPracticeVerdict.Wrong && move.rung != null && move.rung?.trainerId != board.technique
        }
        val before = play.session

        play.play(wrong)

        assertEquals(before, play.session)
        assertTrue(play.status is PracticeStatus.Wrong)
        assertEquals(emptyList<TrainerVerdict>(), play.turns)
    }

    @Test
    fun `an easier move on another rung is undone, names that rung, and reports nothing`() = runTest {
        val (play, easier) = TangoPracticePlay.all(this, BoardKind.Review).firstWithAfterShowing("an easier move on another rung") { board, move ->
            move.verdict is TangoPracticeVerdict.Easier && move.rung?.trainerId != board.technique
        }
        val before = play.session

        play.play(easier)

        assertEquals(before, play.session)
        assertEquals(TangoTrainerCatalog.name(checkNotNull(easier.rung).trainerId), (play.status as PracticeStatus.Easier).technique)
        assertEquals(emptyList<TrainerVerdict>(), play.turns)
    }

    @Test
    fun `a Right move on another rung is kept, is not trained, and reports nothing`() = runTest {
        val (play, right) = TangoPracticePlay.all(this, BoardKind.Review).firstWithAfterShowing("a Right move on another rung") { _, move ->
            val verdict = move.verdict
            verdict is TangoPracticeVerdict.Right && !verdict.trained && verdict.step.rung != null
        }

        play.play(right)

        assertEquals(right.value, play.grid[right.idx])
        val status = play.status
        if (status !is PracticeStatus.Done) {
            val trained = if (right.stepSquares == 1) (status as PracticeStatus.Right).trained else (status as PracticeStatus.KeepGoing).trained
            assertEquals(false, trained)
        }
        assertEquals(emptyList<TrainerVerdict>(), play.turns)
    }
}

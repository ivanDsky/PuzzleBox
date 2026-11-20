package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPractice
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPracticeVerdict
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayFixture
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun `a board opens on the start board with a move of its technique to make`() = runTest {
        val boards = TangoPracticePlay.all(this)
        assertTrue(boards.isNotEmpty())
        for (play in boards) {
            val label = play.technique.value
            assertEquals(label, TangoPractice.start(play.decoded, play.solution, play.technique), play.grid)
            assertEquals(label, PracticeStatus.FindNextMove, play.status)
            assertTrue(label, play.moves().any { it.isTrainedRight })
            assertEquals(label, emptyList<TrainerVerdict>(), play.turns)
            assertNull(label, play.board.spotlight)
            assertFalse(label, play.session.history.canUndo)
            assertEquals(label, 0, play.session.mistakeCount)
            assertNull(label, play.session.ref)
            assertFalse(label, play.session.isComplete)
        }
    }

    @Test
    fun `no board for a technique that is never the easiest, another game, a broken or ambiguous puzzle, or an unknown id`() = runTest {
        fun board(puzzle: Puzzle, technique: TechniqueId) =
            TangoTrainerFixtures.trainer.practice(puzzle, technique, this, onTrainedTurn = {})

        assertNull(board(canonical, TechniqueId("split-pair")))
        assertNull(board(canonical, TechniqueId("no-such-technique")))
        assertNull(board(Puzzle(GameId("other-game"), canonical.variant, canonical.formatVersion, canonical.payload), sign))
        assertNull(board(Puzzle(canonical.gameId, canonical.variant, canonical.formatVersion, byteArrayOf(1, 2, 3)), sign))
        assertNull(board(TangoPlayFixture.envelope(TangoPlayFixture.ambiguous), sign))
    }

    @Test
    fun `every move of every instance of the easiest rung is Right and counts once`() = runTest {
        var severalInstances = 0
        for (start in TangoPracticePlay.all(this)) {
            val rights = start.moves().filter { it.isTrainedRight }
            if (rights.map { (it.verdict as TangoPracticeVerdict.Right).step }.distinct().size > 1) severalInstances++
            for (move in rights) {
                val play = checkNotNull(TangoPracticePlay.open(this, start.puzzle, start.technique))
                val label = "${start.technique.value} ${move.idx}=${move.value}"
                play.play(move)
                assertEquals(label, move.value, play.grid[move.idx])
                assertTrue(label, (play.status as? PracticeStatus.Right)?.trained == true)
                assertEquals(label, listOf(TrainerVerdict.Right), play.turns)
            }
        }
        assertTrue("a start board with several instances of its rung", severalInstances > 0)
    }

    @Test
    fun `a step of several squares is Right square by square and counts once`() = runTest {
        val (play, first) = TangoPracticePlay.all(this).firstWith("a step of several squares") {
            ((it.verdict as? TangoPracticeVerdict.Right)?.step?.squares?.size ?: 0) > 1 && it.isTrainedRight
        }
        val step = (first.verdict as TangoPracticeVerdict.Right).step
        play.play(first)
        assertEquals(listOf(TrainerVerdict.Right), play.turns)

        for (square in step.squares.filter { it.idx != first.idx }) {
            play.play(square.idx, square.value)
            assertEquals(square.value, play.grid[square.idx])
            assertTrue((play.status as? PracticeStatus.Right)?.trained == true)
        }
        assertEquals(listOf(TrainerVerdict.Right), play.turns)
    }

    @Test
    fun `the Spotlight of a Right move stays until the next tap`() = runTest {
        val (play, right) = TangoPracticePlay.all(this).firstWith("a Right move") { it.isTrainedRight }
        play.play(right)
        assertNotNull(play.board.spotlight)

        play.board.onTap(play.emptySquares.first())
        assertNull(play.board.spotlight)
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
        assertNotNull(play.board.spotlight)
        assertEquals(listOf(TrainerVerdict.Wrong), play.turns)

        play.play(right)
        assertEquals(right.value, play.grid[right.idx])
        assertEquals(listOf(TrainerVerdict.Wrong, TrainerVerdict.Right), play.turns)
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
                assertTrue(label, (play.status as? PracticeStatus.Wrong)?.why?.isNotBlank() == true)
            }
            assertEquals(play.technique.value, List(wrong.size) { TrainerVerdict.Wrong }, play.turns)
            wrongMoves += wrong.size
        }
        assertTrue(wrongMoves > 0)
    }

    @Test
    fun `a wrong move on another rung is undone and reports nothing`() = runTest {
        val (play, wrong) = TangoPracticePlay.all(this).firstWithAfterShowing("a wrong move on another rung") { board, move ->
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
        val (play, easier) = TangoPracticePlay.all(this).firstWithAfterShowing("an easier move on another rung") { board, move ->
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
        val (play, right) = TangoPracticePlay.all(this).firstWithAfterShowing("a Right move on another rung") { _, move ->
            val verdict = move.verdict
            verdict is TangoPracticeVerdict.Right && !verdict.trained && verdict.step.rung != null
        }

        play.play(right)

        assertEquals(right.value, play.grid[right.idx])
        assertEquals(false, (play.status as PracticeStatus.Right).trained)
        assertEquals(emptyList<TrainerVerdict>(), play.turns)
    }
}

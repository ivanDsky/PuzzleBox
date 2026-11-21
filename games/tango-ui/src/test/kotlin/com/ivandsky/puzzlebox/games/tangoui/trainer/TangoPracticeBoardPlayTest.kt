package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPractice
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPracticeVerdict
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Tango practice board's tap cycle (a tap cycles a square empty, sun, moon, empty, so a pause or a tap elsewhere
 * settles it), Show me, and the finished board; the judging is in [TangoPracticeBoardTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TangoPracticeBoardPlayTest {

    @Test
    fun `a moon is two taps, and the sun on the way is not judged`() = runTest {
        val (play, moon) = TangoPracticePlay.all(this).firstWith("a Right moon") { it.isTrainedRight && it.value == TangoCell.MOON }

        play.board.onTap(moon.idx)
        assertEquals(TangoCell.SUN, play.grid[moon.idx])
        play.pause(400)
        // A sun here is wrong, but the cycle is not over: still on the board, nothing said, nothing reported.
        assertEquals(TangoCell.SUN, play.grid[moon.idx])
        assertEquals(PracticeStatus.FindNextMove, play.status)

        play.board.onTap(moon.idx)
        play.pause(400)
        // The second tap restarted the pause, so 800 ms after the first tap is still inside it.
        assertEquals(TangoCell.MOON, play.grid[moon.idx])
        assertEquals(PracticeStatus.FindNextMove, play.status)
        assertEquals(emptyList<TrainerVerdict>(), play.turns)

        play.settle()
        assertEquals(TangoCell.MOON, play.grid[moon.idx])
        val status = play.status
        assertTrue(status.isTrainedProgress())
        assertEquals(trainedTurns(moon), play.turns)
    }

    @Test
    fun `a square tapped all the way back to empty is restored with no verdict`() = runTest {
        val play = checkNotNull(TangoPracticePlay.all(this).firstOrNull())
        val before = play.session
        val square = play.emptySquares.first()

        repeat(3) { play.board.onTap(square) }
        play.settle()

        assertEquals(before, play.session)
        assertEquals(PracticeStatus.FindNextMove, play.status)
        assertEquals(emptyList<TrainerVerdict>(), play.turns)
    }

    @Test
    fun `a tap on another square judges the one before at once`() = runTest {
        val play = TangoPracticePlay.all(this).first { board ->
            board.moves().any { it.verdict is TangoPracticeVerdict.Wrong && it.value == TangoCell.SUN } &&
                board.moves().any { it.isTrainedRight && it.value == TangoCell.SUN }
        }
        val before = play.session
        val wrong = play.moves().first { it.verdict is TangoPracticeVerdict.Wrong && it.value == TangoCell.SUN }
        val right = play.moves().first { it.isTrainedRight && it.value == TangoCell.SUN }

        play.board.onTap(wrong.idx)
        assertEquals(TangoCell.SUN, play.grid[wrong.idx])
        play.board.onTap(right.idx)

        assertEquals(before.placements, play.session.placements - right.idx)
        assertTrue(play.status is PracticeStatus.Wrong)
        assertEquals(listOf(TrainerVerdict.Wrong), play.turns)

        play.settle()
        assertEquals(TangoCell.SUN, play.grid[right.idx])
        assertEquals(listOf(TrainerVerdict.Wrong) + trainedTurns(right), play.turns)
    }

    @Test
    fun `a given and a filled square take no tap`() = runTest {
        val (play, right) = TangoPracticePlay.all(this).firstWith("a Right move") { it.isTrainedRight }
        val given = play.decoded.givens.keys.first()
        val before = play.session

        play.board.onTap(given)
        play.settle()
        assertEquals(before, play.session)

        play.play(right)
        val played = play.session
        val status = play.status
        repeat(2) { play.board.onTap(right.idx) }
        play.settle()
        assertEquals(played, play.session)
        assertEquals(status, play.status)
        assertEquals(trainedTurns(right), play.turns)
    }

    @Test
    fun `Show me plays one whole deduction of the easiest rung and reports it for the trained technique`() = runTest {
        for (play in TangoPracticePlay.all(this)) {
            val label = play.technique.value
            val before = play.grid
            val step = checkNotNull(TangoPractice.show(play.decoded, play.solution, before, null))

            play.board.showMe()

            val expected = before.toMutableList().also { grid -> step.squares.forEach { grid[it.idx] = it.value } }
            assertEquals(label, expected, play.grid)
            assertTrue(label, play.status is PracticeStatus.Shown)
            assertEquals(label, listOf(TrainerVerdict.ShowMe), play.turns)
        }
    }

    @Test
    fun `Show me on another rung reports nothing`() = runTest {
        val play = TangoPracticePlay.all(this, BoardKind.Review).first { board ->
            board.showUntil { it.easiestRung?.let { rung -> rung.trainerId != it.technique } == true }
        }
        play.turns.clear()
        val before = play.emptySquares.size

        play.board.showMe()

        assertTrue(play.emptySquares.size < before)
        assertTrue(play.status is PracticeStatus.Shown)
        assertEquals(emptyList<TrainerVerdict>(), play.turns)
    }

    @Test
    fun `Show me stands until Got it, and no move or second Show me lands under it`() = runTest {
        val play = TangoPracticePlay.all(this).first()
        play.board.showMe()
        val shown = play.status
        val after = play.session
        val turns = play.turns.toList()
        val square = play.emptySquares.first()

        play.board.showMe()
        play.play(square, TangoCell.SUN)
        play.board.onPlacement(TangoPlacement(square, TangoCell.MOON))
        play.settle()

        assertTrue(shown is PracticeStatus.Shown)
        assertEquals(shown, play.status)
        assertEquals(after, play.session)
        assertEquals(turns, play.turns)

        play.board.gotIt()

        val ended = TangoPractice.isDone(play.decoded, play.grid, play.technique, play.kind)
        assertEquals(if (ended) PracticeStatus.Done(null) else PracticeStatus.FindNextMove, play.status)
        assertEquals(after, play.session)
    }

    @Test
    fun `Show me finishes the step the player began without counting it twice`() = runTest {
        val (play, first) = TangoPracticePlay.all(this).firstWith("a step of several squares") {
            ((it.verdict as? TangoPracticeVerdict.Right)?.step?.squares?.size ?: 0) > 1 && it.isTrainedRight
        }
        val step = (first.verdict as TangoPracticeVerdict.Right).step
        play.play(first)

        play.board.showMe()

        for (square in step.squares) assertEquals(square.value, play.grid[square.idx])
        assertTrue(play.status is PracticeStatus.Shown)
        assertEquals(listOf(TrainerVerdict.ShowMe), play.turns)
    }

    @Test
    fun `playing every step of a board ends it with the last step's Spotlight, the last tap at once`() = runTest {
        for (kind in BoardKind.entries) {
            for (play in TangoPracticePlay.all(this, kind).take(6)) {
                val label = "${play.technique.value} $kind"
                while (play.status !is PracticeStatus.Done) {
                    val step = checkNotNull(TangoPractice.show(play.decoded, play.solution, play.grid, null))
                    for (square in step.squares) {
                        val last = play.emptySquares.size == 1
                        play.tap(square.idx, square.value)
                        if (last) break
                        play.settle()
                    }
                }
                val done = play.status as PracticeStatus.Done
                assertTrue(label, done.last != null)
                assertTrue(label, TangoPractice.isDone(play.decoded, play.grid, play.technique, kind))
                assertEquals(label, play.session.isComplete, play.emptySquares.isEmpty())
            }
        }
    }

    @Test
    fun `Show me to the end leaves no completion behind`() = runTest {
        for (kind in BoardKind.entries) {
            for (play in TangoPracticePlay.all(this, kind)) {
                val label = "${play.technique.value} $kind"
                while (play.status !is PracticeStatus.Done) {
                    play.board.showMe()
                    assertTrue(label, play.status is PracticeStatus.Shown)
                    play.board.gotIt()
                }

                assertEquals(label, PracticeStatus.Done(null), play.status)
                assertTrue(label, TangoPractice.isDone(play.decoded, play.grid, play.technique, kind))
                assertNull(label, play.session.ref)
            }
        }
    }

    /** The turns a trained Right [move] has reported once played: Right only when it finished its step. */
    private fun trainedTurns(move: TangoPracticePlay.Move): List<TrainerVerdict> =
        if (move.stepSquares == 1) listOf(TrainerVerdict.Right) else emptyList()
}

package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPractice
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a practice board is done (spec §2.2, [BoardKind]) and what a done board does: say Done and take nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class TangoPracticeBoardDoneTest {

    private val order = TangoTrainerFixtures.trainer.catalog().map { it.id }

    @Test
    fun `a board is done exactly when the kind's rule says so, and a review board asks only learned techniques`() = runTest {
        for (kind in BoardKind.entries) {
            for (play in TangoPracticePlay.all(this, kind)) {
                val label = "${play.technique.value} $kind"
                while (play.status !is PracticeStatus.Done) {
                    val rung = play.easiestRung
                    when (kind) {
                        BoardKind.Focus -> assertTrue(label, rung == null || rung.trainerId == play.technique)
                        BoardKind.Review -> assertTrue(label, rung == null || order.indexOf(rung.trainerId) <= order.indexOf(play.technique))
                    }
                    play.playNextStep()
                    assertEquals(label, TangoPractice.isDone(play.decoded, play.grid, play.technique, kind), play.status is PracticeStatus.Done)
                }
            }
        }
    }

    @Test
    fun `a focus board stops with squares left when the next easiest rung is another technique`() = runTest {
        val stopped = TangoPracticePlay.all(this, BoardKind.Focus).filter { play ->
            while (play.status !is PracticeStatus.Done) play.playNextStep()
            play.emptySquares.isNotEmpty()
        }
        assertTrue("some focus board ends unsolved", stopped.isNotEmpty())
        for (play in stopped) assertNotNull(play.easiestRung?.takeIf { it.trainerId != play.technique })
    }

    @Test
    fun `the Right move that ends a board makes it Done with that step's Spotlight and counts once per step`() = runTest {
        for (play in TangoPracticePlay.all(this, BoardKind.Focus)) {
            val label = play.technique.value
            var trainedSteps = 0
            while (play.status !is PracticeStatus.Done) {
                if (play.easiestRung?.trainerId == play.technique) trainedSteps++
                play.playNextStep()
            }

            val done = play.status as PracticeStatus.Done
            assertNotNull(label, done.last)
            assertEquals(label, List(trainedSteps) { TrainerVerdict.Right }, play.turns)
        }
    }

    @Test
    fun `a done board takes no tap, no placement and no Show me, and Got it does nothing`() = runTest {
        val play = TangoPracticePlay.all(this, BoardKind.Focus).first { board ->
            while (board.status !is PracticeStatus.Done) board.playNextStep()
            board.emptySquares.isNotEmpty()
        }
        val session = play.session
        val status = play.status
        val turns = play.turns.toList()
        val square = play.emptySquares.first()

        play.play(square, TangoCell.SUN)
        play.play(square, TangoCell.MOON)
        play.board.onPlacement(TangoPlacement(square, TangoCell.SUN))
        play.board.showMe()
        play.board.gotIt()
        play.settle()

        assertEquals(session, play.session)
        assertEquals(status, play.status)
        assertEquals(turns, play.turns)
    }

    @Test
    fun `Show me that makes the ending step is Shown, and Got it turns it into Done with no Spotlight`() = runTest {
        for (kind in BoardKind.entries) {
            for (play in TangoPracticePlay.all(this, kind)) {
                val label = "${play.technique.value} $kind"
                while (true) {
                    val step = checkNotNull(TangoPractice.show(play.decoded, play.solution, play.grid, null))
                    val after = play.grid.toMutableList().also { cells -> step.squares.forEach { cells[it.idx] = it.value } }
                    if (TangoPractice.isDone(play.decoded, after, play.technique, kind)) break
                    play.playNextStep()
                }

                play.board.showMe()
                assertTrue(label, play.status is PracticeStatus.Shown)
                play.board.showMe()
                assertTrue(label, play.status is PracticeStatus.Shown)
                play.board.gotIt()

                assertEquals(label, PracticeStatus.Done(null, needsOtherTechnique = play.emptySquares.isNotEmpty()), play.status)
            }
        }
    }

    @Test
    fun `a board that ends with squares left says it needs another technique, a solved one does not`() = runTest {
        var unsolved = 0
        var solved = 0
        for (kind in BoardKind.entries) {
            for (play in TangoPracticePlay.all(this, kind)) {
                val label = "${play.technique.value} $kind"
                while (play.status !is PracticeStatus.Done) play.playNextStep()

                val done = play.status as PracticeStatus.Done
                assertEquals(label, play.emptySquares.isNotEmpty(), done.needsOtherTechnique)
                if (done.needsOtherTechnique) unsolved++ else solved++
            }
        }
        assertTrue("some board ends with squares left", unsolved > 0)
        assertTrue("some board is solved", solved > 0)
    }

    @Test
    fun `a focus board that ends on another technique is not solved and says why`() = runTest {
        val stopped = TangoPracticePlay.all(this, BoardKind.Focus).filter { play ->
            while (play.status !is PracticeStatus.Done) play.playNextStep()
            play.emptySquares.isNotEmpty()
        }
        assertTrue(stopped.isNotEmpty())
        for (play in stopped) {
            assertTrue(play.technique.value, (play.status as PracticeStatus.Done).needsOtherTechnique)
        }
    }
}

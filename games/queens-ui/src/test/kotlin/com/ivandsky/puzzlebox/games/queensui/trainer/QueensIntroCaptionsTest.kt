package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensIntro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo captions, built from a hand-made opening so each count of lit squares and landing squares is covered. */
class QueensIntroCaptionsTest {

    private val puzzle = QueensTrainerFixtures.puzzle

    private fun captions(
        technique: String,
        kind: HintReason.Kind,
        unit: Int,
        evidence: Int,
        landing: Int,
        variant: Int = 0,
    ): List<String> {
        val rung = QueensLadder.Stepwise.rungs.first { it.trainerId == TechniqueId(technique) }
        val crossed = (0 until landing).map { Cell(40 + it) }
        val step = QueensStep.Deduction(
            HintReason(kind, unit, rung),
            (0 until evidence).map { Cell(it) }.toSet(),
            QueensStep.Deduction.Result.Cross(crossed.toSet()),
        )
        val board = QueensBoard()
        return QueensIntroCaptions.of(puzzle, TechniqueId(technique), variant, QueensIntro.Opening(board, step, crossed, board))
    }

    @Test
    fun `a demo has a caption for the position, the lit squares, and each landing square`() {
        assertEquals(2 + 3, captions("squeeze", HintReason.Kind.REGION, 3, evidence = 3, landing = 3).size)
        assertEquals(2 + 1, captions("squeeze", HintReason.Kind.REGION, 3, evidence = 3, landing = 1).size)
    }

    @Test
    fun `step one says what is lit, for several squares`() {
        val look = captions("squeeze", HintReason.Kind.REGION, 3, evidence = 3, landing = 2)[1]

        assertEquals("The lit squares are green's last open squares.", look)
    }

    @Test
    fun `step one says what is lit, for one square`() {
        val look = captions("squeeze", HintReason.Kind.ROW, 2, evidence = 1, landing = 1)[1]

        assertEquals("The lit square is row 3's only open square.", look)
    }

    @Test
    fun `the reason sentence comes with the first square, the last square says the result`() {
        val shared = captions("shared-squares", HintReason.Kind.COLUMN, 1, evidence = 2, landing = 3)

        assertTrue(shared[2], shared[2].startsWith("A queen on any of these squares"))
        assertEquals("Cross out this one too.", shared[3])
        assertEquals("Those squares are crossed out.", shared[4])
    }

    @Test
    fun `a color is a group of squares, not a thing to look at`() {
        val singles = captions("singles", HintReason.Kind.REGION, 0, evidence = 7, landing = 1)
        assertEquals("Look at the red squares. Six are crossed out. One is open.", singles[1])

        val fillRegion = captions("line-lock", HintReason.Kind.ROW, 2, evidence = 2, landing = 3, variant = 1)
        assertTrue(fillRegion[2], Regex("cross out the rest of the \\w+ squares\\.").containsMatchIn(fillRegion[2]))
        assertTrue(fillRegion[4], Regex("The rest of the \\w+ squares are crossed out\\.").matches(fillRegion[4]))
    }

    @Test
    fun `a look-ahead says the queens that follow and the line left with no room`() {
        val shortLook = captions("short-look-ahead", HintReason.Kind.REGION, 0, evidence = 4, landing = 2)

        assertEquals("If a queen stood here, the moves it forces would follow, and red would have no room.", shortLook[2])
        assertEquals("Try a queen on the marked square. The lit squares show what follows.", shortLook[1])
        val longLook = captions("long-look-ahead", HintReason.Kind.ROW, 4, evidence = 5, landing = 1)
        assertEquals("If a queen stood here, the moves it forces would follow, and row 5 would have no room.", longLook[2])
    }
}

package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The intro demo's captions: each says what the board shows, whether one square lands or several. */
class TangoDemoCaptionsTest {

    private val puzzle = TangoPlayFixture.puzzle

    private fun deduction(
        rung: TangoStepRung,
        value: TangoCell = TangoCell.SUN,
        row: Int? = null,
        col: Int? = null,
        options: Int? = null,
        idx: Int = 1,
        evidence: Set<Int> = setOf(0),
    ) = TangoStep.Deduction(rung, idx, value, evidence, row, col, options)

    @Test
    fun `frame 0 says one more for one square and a few more for several`() {
        assertEquals("Some squares are filled in. One more can be worked out.", TangoDemoCaptions.opening(landing = 1))
        for (landing in 2..5) {
            val caption = TangoDemoCaptions.opening(landing)
            assertEquals("Some squares are filled in. A few more can be worked out.", caption)
            assertFalse(caption, caption.contains("One more"))
        }
    }

    @Test
    fun `a pair names the one empty end when one square lands and both ends when two do`() {
        val step = deduction(TangoStepRung.PAIR)

        assertEquals("Two moons touch, so the empty end is a sun.", TangoDemoCaptions.reason(step, landing = 1, puzzle))
        assertEquals("Two moons touch, so both ends are suns.", TangoDemoCaptions.reason(step, landing = 2, puzzle))
    }

    @Test
    fun `a pair is looked at as two squares next to each other, not side by side`() {
        val caption = TangoDemoCaptions.evidence(deduction(TangoStepRung.PAIR))

        assertEquals("Look at these two moons next to each other.", caption)
    }

    @Test
    fun `line options says one way, both ways or all N ways, as the line allows`() {
        fun reason(ways: Int) = TangoDemoCaptions.reason(deduction(TangoStepRung.LINE_OPTIONS, options = ways, row = 0), landing = 3, puzzle)

        assertEquals("Only one way to fill this line works, and it puts a sun here.", reason(1))
        assertEquals("Both ways to fill this line put a sun here.", reason(2))
        assertEquals("All 4 ways to fill this line put a sun here.", reason(4))
        assertFalse(reason(1).contains("All 1"))
    }

    @Test
    fun `the last empty square of a full line is named as one, not as the rest`() {
        val step = deduction(TangoStepRung.FULL_LINE, row = 1)

        assertEquals("Row 2 has all its moons, so the last empty square is a sun.", TangoDemoCaptions.reason(step, landing = 1, puzzle))
        assertEquals("Row 2 has all its moons, so the rest are suns.", TangoDemoCaptions.reason(step, landing = 3, puzzle))
    }

    @Test
    fun `a later landing says too only when its kind is the one before`() {
        assertEquals("Same reason, so this one is a sun, too.", TangoDemoCaptions.again(TangoCell.SUN, previous = TangoCell.SUN))
        assertEquals("Same reason: this one is a moon.", TangoDemoCaptions.again(TangoCell.MOON, previous = TangoCell.SUN))
    }

    @Test
    fun `a count squeeze caption spells out the guess and the forced fill`() {
        val step = deduction(TangoStepRung.COUNT_SQUEEZE, col = 0, evidence = setOf(6, 12))

        assertEquals(
            "A moon here would use up the column's moons, so every other empty square would be a sun and break the line. " +
                "So this is a sun.",
            TangoDemoCaptions.reason(step, landing = 1, puzzle),
        )
        assertEquals("Look at the other empty squares in column 1.", TangoDemoCaptions.evidence(step))
    }

    @Test
    fun `a sign count looks at the x pair or at the = pair, as the step reads`() {
        val cross = deduction(TangoStepRung.SIGN_COUNT, col = 5, idx = 3, evidence = setOf(9, 15))
        val equal = deduction(TangoStepRung.SIGN_COUNT, col = 5, idx = 3, evidence = setOf(3, 9))

        assertEquals(
            "Look at column 6. Count its suns and moons, with each empty × pair as one of each.",
            TangoDemoCaptions.evidence(cross),
        )
        assertEquals(
            "Look at the empty = pair in column 6, and count the suns and moons around it.",
            TangoDemoCaptions.evidence(equal),
        )
        assertTrue(TangoDemoCaptions.reason(equal, landing = 2, puzzle).startsWith("Only one moon is left but the = pair needs two"))
    }

    @Test
    fun `no demo caption says cell`() {
        for (rung in TangoStepRung.entries) {
            val step = deduction(rung, row = 0, options = 2)
            for (landing in 1..3) {
                val text = TangoDemoCaptions.evidence(step) + TangoDemoCaptions.reason(step, landing, puzzle)
                assertFalse("$rung: $text", text.contains("cell", ignoreCase = true))
            }
        }
    }
}

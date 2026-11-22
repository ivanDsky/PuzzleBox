package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoStepCopyTest {

    private val variant = VariantId("tango/n4")

    private fun puzzle(vararg markers: TangoMarker) = TangoPuzzle(variant, 4, emptyMap(), markers.toList())

    private val plain = puzzle()

    private val sign = puzzle(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))

    private fun deduction(
        rung: TangoStepRung,
        value: TangoCell = TangoCell.SUN,
        idx: Int = 1,
        evidence: Set<Int> = setOf(0),
        row: Int? = null,
        col: Int? = null,
        options: Int? = null,
    ) = TangoStep.Deduction(rung, idx, value, evidence, row, col, options)

    private fun text(step: TangoStep, puzzle: TangoPuzzle = plain) = TangoStepCopy.text(step, puzzle)

    /** One step of every kind: each rung (the sign on a puzzle that holds its marker), the reveal and every mistake. */
    private fun everyStep(): List<Pair<TangoStep, TangoPuzzle>> =
        TangoStepRung.entries.map { deduction(it, row = 1, options = 2, evidence = setOf(0, 1)) to sign } +
            listOf(null, TangoVerdict.Reason.ThreeInARow, TangoVerdict.Reason.MarkerConflict, TangoVerdict.Reason.CountExceeded)
                .map { TangoStep.Mistake(1, it) to plain } +
            (TangoStep.Reveal(1, TangoCell.MOON) to plain)

    @Test
    fun `each rung's text is pinned for both values`() {
        val expected = mapOf(
            TangoStepRung.PAIR to listOf(
                "Two moons touch, so an empty end next to them is a sun",
                "Two suns touch, so an empty end next to them is a moon",
            ),
            TangoStepRung.SANDWICH to listOf(
                "This square sits between two moons, so it is a sun",
                "This square sits between two suns, so it is a moon",
            ),
            TangoStepRung.FULL_LINE to listOf(
                "Row 2 has all its moons, so the rest are suns",
                "Row 2 has all its suns, so the rest are moons",
            ),
            TangoStepRung.EQUAL_PAIR to listOf(
                "Joined by =, these would make three moons in a row, so both are suns",
                "Joined by =, these would make three suns in a row, so both are moons",
            ),
            TangoStepRung.SIGN_COUNT to listOf(
                "Counting the signs, no moon fits here, so this is a sun",
                "Counting the signs, no sun fits here, so this is a moon",
            ),
            TangoStepRung.SPLIT_PAIR to listOf(
                "The gap holds one of each, so the rest are suns",
                "The gap holds one of each, so the rest are moons",
            ),
            TangoStepRung.COUNT_SQUEEZE to listOf(
                "A moon here uses up the moons and breaks the line, so this is a sun",
                "A sun here uses up the suns and breaks the line, so this is a moon",
            ),
            TangoStepRung.LINE_OPTIONS to listOf(
                "Both ways to fill this line put a sun here",
                "Both ways to fill this line put a moon here",
            ),
        )
        assertEquals("a rung has no pinned text", TangoStepRung.entries.toSet() - TangoStepRung.SIGN, expected.keys)
        for ((rung, texts) in expected) {
            val actual = listOf(TangoCell.SUN, TangoCell.MOON).map { text(deduction(rung, it, row = 1, options = 2)) }
            assertEquals("$rung", texts, actual)
        }
    }

    @Test
    fun `a full column is named by its column`() {
        assertEquals(
            "Column 3 has all its moons, so the rest are suns",
            text(deduction(TangoStepRung.FULL_LINE, col = 2)),
        )
    }

    @Test
    fun `reveal and mistake variants are pinned`() {
        assertEquals("This square is a moon, though no simple step finds it", text(TangoStep.Reveal(1, TangoCell.MOON)))
        assertEquals("This square isn't in the solution", text(TangoStep.Mistake(1, null)))
        assertEquals("This square makes three in a row", text(TangoStep.Mistake(1, TangoVerdict.Reason.ThreeInARow)))
        assertEquals("This square breaks a sign", text(TangoStep.Mistake(1, TangoVerdict.Reason.MarkerConflict)))
        assertEquals("This square has too many of one kind", text(TangoStep.Mistake(1, TangoVerdict.Reason.CountExceeded)))
    }

    @Test
    fun `no text starts with a deduction name, and each is one short line`() {
        val names = listOf("Pair", "Sandwich", "Full line", "Equal pair", "Sign count", "Sign", "Split pair", "Count squeeze", "Line options", "Reveal", "Wrong square")
        for ((step, p) in everyStep()) {
            val line = text(step, p)
            assertFalse(line, '\n' in line)
            assertTrue("$line is ${line.length} chars", line.length <= 70)
            assertFalse(line, Regex("^[A-Z][A-Za-z ]+: ").containsMatchIn(line))
            for (name in names) assertFalse("$line starts with $name", line.startsWith("$name:", ignoreCase = true))
        }
    }

    @Test
    fun `the sign text follows the marker the puzzle holds and the target's own value`() {
        val same = puzzle(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME))
        val opposite = puzzle(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))
        val signStep = { value: TangoCell -> deduction(TangoStepRung.SIGN, value, idx = 1, evidence = setOf(0, 1)) }

        assertEquals("An = sign copies its neighbor, so this is a sun", text(signStep(TangoCell.SUN), same))
        assertEquals("An = sign copies its neighbor, so this is a moon", text(signStep(TangoCell.MOON), same))
        assertEquals("A × sign flips its neighbor, so this is a sun", text(signStep(TangoCell.SUN), opposite))
        assertEquals("A × sign flips its neighbor, so this is a moon", text(signStep(TangoCell.MOON), opposite))
    }

    @Test
    fun `the sign text finds a marker below the target too`() {
        val below = puzzle(TangoMarker(1, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE))

        assertEquals(
            "A × sign flips its neighbor, so this is a moon",
            text(deduction(TangoStepRung.SIGN, TangoCell.MOON, idx = 5, evidence = setOf(1, 5)), below),
        )
    }

    @Test
    fun `a sign step with no marker found uses the fallback`() {
        assertEquals(
            "The sign beside it makes this a moon",
            text(deduction(TangoStepRung.SIGN, TangoCell.MOON, idx = 1, evidence = setOf(0, 1)), plain),
        )
    }

    @Test
    fun `a line options text counts its ways, in plain words for one, two and many`() {
        fun ways(options: Int) = text(deduction(TangoStepRung.LINE_OPTIONS, row = 1, options = options))
        assertEquals("Only one way to fill this line works, and it puts a sun here", ways(1))
        assertEquals("Both ways to fill this line put a sun here", ways(2))
        assertEquals("All 3 ways to fill this line put a sun here", ways(3))
        assertEquals("All 12 ways to fill this line put a sun here", ways(12))
    }

    @Test
    fun `a sign count step through an = pair names the pair, one through a x pair counts the signs`() {
        // An = pair is its own evidence: the target is among the evidence cells.
        assertEquals(
            "Only one moon is left but the = pair needs two, so both are suns",
            text(deduction(TangoStepRung.SIGN_COUNT, TangoCell.SUN, idx = 1, evidence = setOf(0, 1))),
        )
        assertEquals(
            "Only one sun is left but the = pair needs two, so both are moons",
            text(deduction(TangoStepRung.SIGN_COUNT, TangoCell.MOON, idx = 1, evidence = setOf(0, 1))),
        )
        assertEquals(
            "Counting the signs, no moon fits here, so this is a sun",
            text(deduction(TangoStepRung.SIGN_COUNT, TangoCell.SUN, idx = 5, evidence = setOf(0, 1))),
        )
    }

    @Test
    fun `no hint text says cell`() {
        for ((step, p) in everyStep()) assertFalse(text(step, p), "cell" in text(step, p).lowercase())
    }
}

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

    @Test
    fun `each rung's text is pinned for both values`() {
        val expected = mapOf(
            TangoStepRung.PAIR to listOf("Pair: two moons touch, so suns at both ends", "Pair: two suns touch, so moons at both ends"),
            TangoStepRung.SANDWICH to listOf("Sandwich: a sun goes between two moons", "Sandwich: a moon goes between two suns"),
            TangoStepRung.FULL_LINE to listOf(
                "Full line: row 2 has all its moons, the rest are suns",
                "Full line: row 2 has all its suns, the rest are moons",
            ),
            TangoStepRung.EQUAL_PAIR to listOf("Equal pair: beside a moon, so both are suns", "Equal pair: beside a sun, so both are moons"),
            TangoStepRung.SIGN_COUNT to listOf("Sign count: the signs use up the moons, so a sun", "Sign count: the signs use up the suns, so a moon"),
            TangoStepRung.SPLIT_PAIR to listOf("Split pair: gap holds 1 each, rest suns", "Split pair: gap holds 1 each, rest moons"),
            TangoStepRung.COUNT_SQUEEZE to listOf(
                "Count squeeze: the other value here would break the line, so a sun",
                "Count squeeze: the other value here would break the line, so a moon",
            ),
            TangoStepRung.LINE_OPTIONS to listOf("Line options: all 2 fills, a sun here", "Line options: all 2 fills, a moon here"),
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
            "Full line: column 3 has all its moons, the rest are suns",
            text(deduction(TangoStepRung.FULL_LINE, col = 2)),
        )
    }

    @Test
    fun `reveal and mistake variants are pinned`() {
        assertEquals("Reveal: this cell is a moon, no simple step", text(TangoStep.Reveal(1, TangoCell.MOON)))
        assertEquals("Wrong cell: this one isn't in the solution", text(TangoStep.Mistake(1, null)))
        assertEquals("Wrong cell: three in a row", text(TangoStep.Mistake(1, TangoVerdict.Reason.ThreeInARow)))
        assertEquals("Wrong cell: breaks a sign", text(TangoStep.Mistake(1, TangoVerdict.Reason.MarkerConflict)))
        assertEquals("Wrong cell: too many of one kind", text(TangoStep.Mistake(1, TangoVerdict.Reason.CountExceeded)))
    }

    @Test
    fun `every text is one short line that starts with its name`() {
        val steps = TangoStepRung.entries.map { deduction(it, row = 1, options = 2) } +
            listOf(null, TangoVerdict.Reason.ThreeInARow, TangoVerdict.Reason.MarkerConflict, TangoVerdict.Reason.CountExceeded)
                .map { TangoStep.Mistake(1, it) } +
            TangoStep.Reveal(1, TangoCell.MOON)
        for (step in steps) {
            for (p in listOf(plain, puzzle(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE)))) {
                val line = text(step, p)
                assertFalse(line, '\n' in line)
                assertTrue("$line is ${line.length} chars", line.length <= 70)
                assertTrue(line, Regex("^[A-Z][A-Za-z ]+: ").containsMatchIn(line))
            }
        }
    }

    @Test
    fun `the sign text follows the marker the puzzle holds and the target's own value`() {
        val same = puzzle(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME))
        val opposite = puzzle(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))
        val sign = { value: TangoCell -> deduction(TangoStepRung.SIGN, value, idx = 1, evidence = setOf(0, 1)) }

        assertEquals("Sign: = copies its neighbor, so this is a sun", text(sign(TangoCell.SUN), same))
        assertEquals("Sign: = copies its neighbor, so this is a moon", text(sign(TangoCell.MOON), same))
        assertEquals("Sign: × flips its neighbor, so this is a sun", text(sign(TangoCell.SUN), opposite))
        assertEquals("Sign: × flips its neighbor, so this is a moon", text(sign(TangoCell.MOON), opposite))
    }

    @Test
    fun `the sign text finds a marker below the target too`() {
        val below = puzzle(TangoMarker(1, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE))

        assertEquals(
            "Sign: × flips its neighbor, so this is a moon",
            text(deduction(TangoStepRung.SIGN, TangoCell.MOON, idx = 5, evidence = setOf(1, 5)), below),
        )
    }

    @Test
    fun `a sign step with no marker found uses the fallback`() {
        assertEquals(
            "Sign: the sign beside it makes this a moon",
            text(deduction(TangoStepRung.SIGN, TangoCell.MOON, idx = 1, evidence = setOf(0, 1)), plain),
        )
    }

    // HintPolish (#1174): the plain set, one sentence per step kind and no deduction name in front.

    private fun plainText(step: TangoStep, puzzle: TangoPuzzle = plain) = TangoStepCopy.text(step, puzzle, plain = true)

    private val sign = puzzle(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))

    /** One step of every kind: each rung (the sign on a puzzle that holds its marker), the reveal and every mistake. */
    private fun everyStep(): List<Pair<TangoStep, TangoPuzzle>> =
        TangoStepRung.entries.map { deduction(it, row = 1, options = 2, evidence = setOf(0, 1)) to sign } +
            listOf(null, TangoVerdict.Reason.ThreeInARow, TangoVerdict.Reason.MarkerConflict, TangoVerdict.Reason.CountExceeded)
                .map { TangoStep.Mistake(1, it) to plain } +
            (TangoStep.Reveal(1, TangoCell.MOON) to plain)

    @Test
    fun `plain texts are pinned for each rung, the reveal and the mistakes`() {
        val expected = mapOf(
            TangoStepRung.PAIR to "Two moons touch, so the cells at both ends are suns",
            TangoStepRung.SANDWICH to "This cell sits between two moons, so it is a sun",
            TangoStepRung.FULL_LINE to "Row 2 has all its moons, so the rest are suns",
            TangoStepRung.EQUAL_PAIR to "Beside a moon, so both of these cells are suns",
            TangoStepRung.SIGN_COUNT to "The signs use up the moons, so this is a sun",
            TangoStepRung.SPLIT_PAIR to "The gap holds one of each, so the rest are suns",
            TangoStepRung.COUNT_SQUEEZE to "The other kind here would break the line, so this is a sun",
            TangoStepRung.LINE_OPTIONS to "All 2 ways to fill this line put a sun here",
            TangoStepRung.SIGN to "A × sign flips its neighbor, so this is a sun",
        )
        assertEquals(TangoStepRung.entries.toSet(), expected.keys)
        for ((rung, line) in expected) {
            assertEquals("$rung", line, plainText(deduction(rung, row = 1, options = 2, evidence = setOf(0, 1)), sign))
        }
        assertEquals(
            "An = sign copies its neighbor, so this is a moon",
            plainText(
                deduction(TangoStepRung.SIGN, TangoCell.MOON, evidence = setOf(0, 1)),
                puzzle(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)),
            ),
        )
        assertEquals("The sign beside it makes this a moon", plainText(deduction(TangoStepRung.SIGN, TangoCell.MOON, evidence = setOf(0, 1))))
        assertEquals("Column 3 has all its moons, so the rest are suns", plainText(deduction(TangoStepRung.FULL_LINE, col = 2)))
        assertEquals("This cell is a moon, though no simple step finds it", plainText(TangoStep.Reveal(1, TangoCell.MOON)))
        assertEquals("This cell isn't in the solution", plainText(TangoStep.Mistake(1, null)))
        assertEquals("This cell makes three in a row", plainText(TangoStep.Mistake(1, TangoVerdict.Reason.ThreeInARow)))
        assertEquals("This cell breaks a sign", plainText(TangoStep.Mistake(1, TangoVerdict.Reason.MarkerConflict)))
        assertEquals("This cell has too many of one kind", plainText(TangoStep.Mistake(1, TangoVerdict.Reason.CountExceeded)))
    }

    @Test
    fun `no plain text starts with a deduction name, and each is one short line`() {
        val names = listOf("Pair", "Sandwich", "Full line", "Equal pair", "Sign count", "Sign", "Split pair", "Count squeeze", "Line options", "Reveal", "Wrong cell")
        for ((step, p) in everyStep()) {
            val line = plainText(step, p)
            assertFalse(line, '\n' in line)
            assertTrue("$line is ${line.length} chars", line.length <= 70)
            assertFalse(line, Regex("^[A-Z][A-Za-z ]+: ").containsMatchIn(line))
            for (name in names) assertFalse("$line starts with $name", line.startsWith("$name:", ignoreCase = true))
        }
    }

    @Test
    fun `flag off keeps today's line for a rung`() {
        val step = deduction(TangoStepRung.PAIR)
        assertEquals("Pair: two moons touch, so suns at both ends", TangoStepCopy.text(step, plain, plain = false))
        assertEquals(text(step), TangoStepCopy.text(step, plain, plain = false))
    }
}

package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.games.tango.render.TangoViolations.Violation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The wrong-move line shown after the move is undone: it names the rule, not squares that are no longer there. */
class TangoWrongMoveCopyTest {

    private fun marker(kind: TangoMarkerKind) = Violation(
        cells = setOf(3, 4),
        reason = TangoVerdict.Reason.MarkerConflict,
        marker = TangoRendererModel.MarkerRender(3, TangoEdgeSide.RIGHT, kind),
        phrase = "These two must be the same.",
    )

    private fun run(phrase: String) = Violation(setOf(0, 1, 2), TangoVerdict.Reason.ThreeInARow, null, phrase)

    private fun count(phrase: String) = Violation(setOf(0, 1, 2, 3), TangoVerdict.Reason.CountExceeded, null, phrase)

    @Test
    fun `a broken sign names the sign beside the square`() {
        assertEquals("That breaks the = sign beside it.", TangoWrongMoveCopy.why(listOf(marker(TangoMarkerKind.SAME)), idx = 3))
        assertEquals("That breaks the × sign beside it.", TangoWrongMoveCopy.why(listOf(marker(TangoMarkerKind.OPPOSITE)), idx = 4))
    }

    @Test
    fun `three in a row is what the move would make, in the row or the column`() {
        assertEquals("That would make three suns in a row.", TangoWrongMoveCopy.why(listOf(run("Three suns in a row.")), idx = 1))
        assertEquals("That would make three moons in a column.", TangoWrongMoveCopy.why(listOf(run("Three moons in a column.")), idx = 1))
    }

    @Test
    fun `an over-full line is what the move would put in it, not what stands`() {
        assertEquals(
            "That would put 4 suns in row 2, and a row holds 3.",
            TangoWrongMoveCopy.why(listOf(count("Row 2 has 4 suns — a row holds 3.")), idx = 0),
        )
        assertEquals(
            "That would put 4 moons in column 6, and a column holds 3.",
            TangoWrongMoveCopy.why(listOf(count("Column 6 has 4 moons — a column holds 3.")), idx = 0),
        )
    }

    @Test
    fun `a violation through the move wins over one elsewhere, and a board with none has no line`() {
        val elsewhere = Violation(setOf(20, 21, 22), TangoVerdict.Reason.ThreeInARow, null, "Three moons in a row.")

        assertEquals("That would make three suns in a row.", TangoWrongMoveCopy.why(listOf(elsewhere, run("Three suns in a row.")), idx = 2))
        assertNull(TangoWrongMoveCopy.why(emptyList(), idx = 2))
    }

    @Test
    fun `no wrong-move line points at squares with these two`() {
        for (kind in TangoMarkerKind.entries) {
            val text = TangoWrongMoveCopy.why(listOf(marker(kind)), idx = 3)!!
            assertEquals(text, false, text.contains("These two"))
        }
    }
}

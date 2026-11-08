package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tango notation v1 (M5-12-a): parse/print round trips, the four canonical fixtures and the
 * error coordinates. The whole-text blanking (header + `#` lines → "") is simulated here the
 * same way [com.ivandsky.puzzlebox.game.editor.NotationText] does it, so line numbers are
 * whole-text and 1-based, exactly what the real caller passes.
 */
class TangoNotationTest {

    private fun blanked(vararg lines: String): String = lines.joinToString("\n") {
        if (it.startsWith("puzzlebox ") || it.startsWith("#")) "" else it
    } + "\n"

    private fun blanked(text: String): String =
        blanked(*text.lines().dropLastWhile { it.isEmpty() }.toTypedArray())

    private fun parse(text: String): NotationResult<TangoDraft> = TangoNotation.parse(text)

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/notation/$name")!!.readBytes().decodeToString()

    // --- parse(print(d)) == Ok(d) -----------------------------------------------------------

    @Test
    fun `round trip blank draft`() {
        val d = TangoEditor.newDraft(TangoEditor.variants.single())
        assertEquals(NotationResult.Ok(d), parse(blanked(TangoNotation.print(d))))
    }

    @Test
    fun `round trip fully set draft`() {
        val d = draft(
            "SMSMSM",
            "MSMSMS",
            "SMSMSM",
            "MSMSMS",
            "SMSMSM",
            "MSMSMS",
            markers = listOf(
                TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoMarker(7, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            ),
        )
        assertEquals(NotationResult.Ok(d), parse(blanked(TangoNotation.print(d))))
    }

    @Test
    fun `round trip partly set draft built through editor commands`() {
        var d = TangoEditor.newDraft(TangoEditor.variants.single())
        for (command in listOf(
            d.cycleCell(0),
            d.cycleCell(1),
            d.cycleMarker(6, TangoEdgeSide.DOWN),
            d.cycleMarker(13, TangoEdgeSide.RIGHT),
        )) {
            d = command.apply(d)
        }
        assertEquals(NotationResult.Ok(d), parse(blanked(TangoNotation.print(d))))
    }

    private fun draft(vararg rows: String, markers: List<TangoMarker> = emptyList()): TangoDraft =
        TangoDraft(
            givens = rows.flatMapIndexed { r, row ->
                row.mapIndexedNotNull { c, ch ->
                    when (ch) {
                        'S' -> r * TangoDraft.N + c to TangoCell.SUN
                        'M' -> r * TangoDraft.N + c to TangoCell.MOON
                        else -> null
                    }
                }
            }.toMap(),
            markers = markers,
        )

    // --- fixtures ---------------------------------------------------------------------------

    @Test
    fun `fixtures parse, print canonically and match their expect line`() {
        for (name in listOf(
            "tango-unique.txt",
            "tango-needs-guessing.txt",
            "tango-multiple.txt",
            "tango-none.txt",
        )) {
            val text = fixture(name)
            val expect = text.lineSequence().first { it.startsWith("# expect:") }
                .removePrefix("# expect:").trim().split(" ")
            val wantedVerdict = expect.first()
            val wantedBlocks = expect.drop(1)

            val parsed = parse(blanked(text))
            assertTrue("$name should parse", parsed is NotationResult.Ok)
            val d = (parsed as NotationResult.Ok).draft

            assertEquals(
                "$name print not canonical",
                text.trimEnd('\n').lines().filterNot { it.startsWith("#") }.joinToString("\n") + "\n",
                TangoNotation.print(d),
            )

            val verdict = TangoEditor.analyze(d, Budget.Unlimited)
            assertEquals("$name verdict", wantedVerdict, verdict::class.simpleName!!.lowercase())
            val blockIds = TangoEditor.publishBlocks(d, verdict).map { it.id }
            assertEquals("$name blocks", wantedBlocks, blockIds)
        }
    }

    // --- canonical marker order ---------------------------------------------------------------

    @Test
    fun `parsed markers are canonical, one per edge, so undo equality holds`() {
        val text = blanked(
            "",
            "......",
            "......",
            "......",
            "......",
            "......",
            "......",
            "x 3,4 down",
            "= 1,2 right",
            "= 1,2 down",
        )
        val parsed = parse(text)
        assertTrue(parsed is NotationResult.Ok)
        val d = (parsed as NotationResult.Ok).draft
        assertEquals(
            listOf(
                TangoMarker(1, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoMarker(1, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(15, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            ),
            d.markers,
        )
        val command = TangoDraftCommand.SetMarker(1, TangoEdgeSide.RIGHT, null, TangoMarkerKind.SAME)
        assertEquals(d, command.revert(command.apply(d)))
    }

    // --- errors: one per grammar rule ---------------------------------------------------------

    @Test
    fun `too few rows errors at the last row`() {
        val err = parse(blanked("", "SMSMSM", "MSMSMS", "SMSMSM"))
        assertEquals(NotationResult.Err(4, 1, "board is 3 rows; expected 6"), err)
    }

    @Test
    fun `too many rows errors at the first extra row`() {
        val extra = List(7) { "......" }.toTypedArray()
        val err = parse(blanked("", *extra))
        assertEquals(NotationResult.Err(8, 1, "expected a marker line starting with '=' or 'x'"), err)
    }

    @Test
    fun `short row errors at col 1`() {
        val err = parse(blanked("", "SMSMSM", "MSMSM", "SMSMSM", "MSMSMS", "SMSMSM", "MSMSMS"))
        assertEquals(NotationResult.Err(3, 1, "row 2 has 5 cells, expected 6"), err)
    }

    @Test
    fun `bad character errors at its line and column`() {
        val err = parse(blanked("", "SMSMSM", "MSXMSM", "SMSMSM", "MSMSMS", "SMSMSM", "MSMSMS"))
        assertEquals(NotationResult.Err(3, 3, "unexpected character 'X'"), err)
    }

    @Test
    fun `space inside a row errors at the space`() {
        val err = parse(blanked("", "SMS SM", "MSMSMS", "SMSMSM", "MSMSMS", "SMSMSM", "MSMSMS"))
        assertEquals(NotationResult.Err(2, 4, "unexpected character ' '"), err)
    }

    @Test
    fun `malformed marker line errors at col 1`() {
        val err = parse(blanked("", "......", "......", "......", "......", "......", "......", "y 1,1 right"))
        assertEquals(NotationResult.Err(8, 1, "expected a marker line starting with '=' or 'x'"), err)
    }

    @Test
    fun `malformed coordinates error at the coordinate token`() {
        val err = parse(blanked("", "......", "......", "......", "......", "......", "......", "= 1,right"))
        assertEquals(NotationResult.Err(8, 3, "malformed coordinates"), err)
    }

    @Test
    fun `off-board cell error at the coordinate token`() {
        val err = parse(blanked("", "......", "......", "......", "......", "......", "......", "= 1,7 right"))
        assertEquals(NotationResult.Err(8, 3, "cell 1,7 outside the board"), err)
    }

    @Test
    fun `border edge error at the coordinate token`() {
        val err = parse(blanked("", "......", "......", "......", "......", "......", "......", "x 1,6 right"))
        assertEquals(NotationResult.Err(8, 3, "edge 1,6 right is a border edge"), err)
    }

    @Test
    fun `second marker on one edge errors at the coordinate token`() {
        val err = parse(
            blanked(
                "", "......", "......", "......", "......", "......", "......",
                "= 1,1 right", "x 1,1 right",
            ),
        )
        assertEquals(NotationResult.Err(9, 3, "edge 1,1 right already has a marker"), err)
    }

    @Test
    fun `bad direction token errors at the token`() {
        val err = parse(blanked("", "......", "......", "......", "......", "......", "......", "= 1,1 left"))
        assertEquals(NotationResult.Err(8, 7, "expected direction 'right' or 'down'"), err)
    }

    @Test
    fun `unexpected marker line before the rows error`() {
        val err = parse(blanked("", "= 1,1 right", "......", "......", "......", "......", "......"))
        assertEquals(NotationResult.Err(2, 1, "row 1 has 11 cells, expected 6"), err)
    }

    // --- structure ---------------------------------------------------------------------------

    @Test
    fun `notation version is 1 and columns is N`() {
        assertEquals(1, TangoNotation.notationVersion)
        val d = draft("......", "......", "......", "......", "......", "......")
        assertEquals(TangoDraft.N, TangoNotation.columns(d))
    }
}

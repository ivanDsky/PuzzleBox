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
            6,
            givens = rows.flatMapIndexed { r, row ->
                row.mapIndexedNotNull { c, ch ->
                    when (ch) {
                        'S' -> r * 6 + c to TangoCell.SUN
                        'M' -> r * 6 + c to TangoCell.MOON
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

    /**
     * The 4×4, 8×8 and 10×10 fixtures carry `# verdict:`, not `# expect:`, so the CLI parity and
     * doc tests skip them until M4-tango-27 offers those sizes (NotationText answers "unsupported
     * size" and the v1 draft codec holds only 6×6 before then). The verdict is checked here.
     */
    @Test
    fun `sized fixtures parse, print byte-identically and are unique with no block`() {
        for ((name, n) in listOf(
            "tango-unique-4x4.txt" to 4,
            "tango-unique-8x8.txt" to 8,
            "tango-unique-10x10.txt" to 10,
        )) {
            val text = fixture(name)
            val wanted = text.lineSequence().first { it.startsWith("# verdict:") }.removePrefix("# verdict:").trim()

            val parsed = parse(blanked(text))
            assertTrue("$name should parse, got $parsed", parsed is NotationResult.Ok)
            val d = (parsed as NotationResult.Ok).draft
            assertEquals("$name size", n, d.n)
            assertEquals(
                "$name print not byte-identical",
                text.trimEnd('\n').lines().filterNot { it.startsWith("#") }.joinToString("\n") + "\n",
                TangoNotation.print(d),
            )

            val verdict = TangoEditor.analyze(d, Budget.Unlimited)
            assertEquals("$name verdict", wanted, verdict::class.simpleName!!.lowercase())
            assertEquals("$name blocks", emptyList<String>(), TangoEditor.publishBlocks(d, verdict).map { it.id })
        }
    }

    @Test
    fun `round trips at every size`() {
        for (n in TangoDraft.SIZES) {
            val d = TangoDraft(
                n,
                givens = mapOf(0 to TangoCell.SUN, n * n - 1 to TangoCell.MOON),
                markers = listOf(
                    TangoMarker(n - 2, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                    TangoMarker(n * (n - 2), TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                ),
            )
            val printed = TangoNotation.print(d)
            assertEquals("n=$n", NotationResult.Ok(d), parse(blanked(printed)))
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
    fun `unexpected marker line before the rows errors at the marker`() {
        val err = parse(blanked("", "= 1,1 right", "......", "......", "......", "......", "......"))
        assertEquals(NotationResult.Err(2, 1, "unexpected character '='"), err)
    }

    @Test
    fun `a 10-char marker line before 6x6 rows errors at the marker, not as a 10-row board`() {
        val err = parse(blanked("", "= 1,2 down", "......", "......", "......", "......", "......", "......"))
        assertEquals(NotationResult.Err(2, 1, "unexpected character '='"), err)
    }

    @Test
    fun `a leading space in the first row errors at the space`() {
        val err = parse(blanked("", " SMSM", "MSMS", "SMSM", "MSMS"))
        assertEquals(NotationResult.Err(2, 1, "unexpected character ' '"), err)
    }

    @Test
    fun `a spaced first row errors at the first space, not as a board width`() {
        val err = parse(blanked("", "S M S M S M", "MSMSMS", "SMSMSM", "MSMSMS", "SMSMSM", "MSMSMS"))
        assertEquals(NotationResult.Err(2, 2, "unexpected character ' '"), err)
    }

    @Test
    fun `CRLF text parses at its row width`() {
        val text = listOf("", "S...", "....", "....", "...M", "= 1,1 right", "").joinToString("\r\n")
        val wanted = TangoDraft(
            4,
            givens = mapOf(0 to TangoCell.SUN, 15 to TangoCell.MOON),
            markers = listOf(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)),
        )
        assertEquals(NotationResult.Ok(wanted), parse(text))
    }

    @Test
    fun `a first row narrower than the rest sets n and the next row errors`() {
        val err = parse(blanked("", "....", "........", "........", "........"))
        assertEquals(NotationResult.Err(3, 1, "row 2 has 8 cells, expected 4"), err)
    }

    // --- size from the first row's width -------------------------------------------------------

    @Test
    fun `no rows after the header errors at line 1`() {
        assertEquals(NotationResult.Err(1, 1, "no rows after the header"), parse(blanked("")))
    }

    @Test
    fun `a 5-wide first row errors with the sizes at its line`() {
        val err = parse(blanked("", "SMSMS", "MSMSM", "SMSMS", "MSMSM", "SMSMS"))
        assertEquals(NotationResult.Err(2, 1, "board is 5 wide; Tango sizes are 4, 6, 8, 10"), err)
    }

    @Test
    fun `a short row in an 8x8 board errors at col 1`() {
        val rows = List(8) { "........" }.toMutableList().apply { this[2] = "......." }
        val err = parse(blanked("", *rows.toTypedArray()))
        assertEquals(NotationResult.Err(4, 1, "row 3 has 7 cells, expected 8"), err)
    }

    @Test
    fun `seven rows of 8 error at the last row`() {
        val err = parse(blanked("", *List(7) { "........" }.toTypedArray()))
        assertEquals(NotationResult.Err(8, 1, "board is 7 rows; expected 8"), err)
    }

    @Test
    fun `a marker past the 8x8 edge errors at the coordinate token`() {
        val board = List(8) { "........" }.toTypedArray()
        fun marker(line: String) = parse(blanked("", *board, line))
        assertEquals(NotationResult.Err(10, 3, "cell 9,1 outside the board"), marker("= 9,1 down"))
        assertEquals(NotationResult.Err(10, 3, "edge 1,8 right is a border edge"), marker("x 1,8 right"))
        assertEquals(NotationResult.Err(10, 3, "edge 8,1 down is a border edge"), marker("x 8,1 down"))
    }

    // --- structure ---------------------------------------------------------------------------

    @Test
    fun `notation version is 1 and columns is N`() {
        assertEquals(1, TangoNotation.notationVersion)
        val d = draft("......", "......", "......", "......", "......", "......")
        assertEquals(6, TangoNotation.columns(d))
        assertEquals(8, TangoNotation.columns(TangoDraft(8, emptyMap(), emptyList())))
    }
}

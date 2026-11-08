package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Queens notation v1 (M5-01-b): parse/print round trips, the three canonical fixtures and the
 * error coordinates. The whole-text blanking (header + `#` lines → "") is simulated here the
 * same way [com.ivandsky.puzzlebox.game.editor.NotationText] does it, so line numbers are
 * whole-text and 1-based, exactly what the real caller passes.
 */
class QueensNotationTest {

    private fun blanked(vararg lines: String): String = lines.joinToString("\n") {
        if (it.startsWith("puzzlebox ") || it.startsWith("#")) "" else it
    } + "\n"

    private fun parse(text: String): NotationResult<QueensDraft> = QueensEditor.parse(text)

    private fun blanked(text: String): String =
        blanked(*text.lines().dropLastWhile { it.isEmpty() }.toTypedArray())

    private fun draft(size: Int, vararg rows: String): QueensDraft =
        QueensDraft(size, rows.joinToString("").map {
            when (it) {
                '.' -> QueensDraft.UNPAINTED
                in '0'..'9' -> it - '0'
                else -> it - 'a' + 10
            }
        })

    // --- parse(print(d)) == Ok(d) -----------------------------------------------------------

    @Test
    fun `round trip painted draft`() {
        val d = draft(5, "00111", "02211", "02333", "44433", "44443")
        assertEquals(NotationResult.Ok(d), parse(blanked(QueensNotation.print(d))))
    }

    @Test
    fun `round trip partly unpainted draft`() {
        val d = draft(4, "00..", "01.1", "2111", "2112")
        assertEquals(NotationResult.Ok(d), parse(blanked(QueensNotation.print(d))))
    }

    @Test
    fun `round trip 12x12 draft`() {
        val rows = List(12) { r -> (0 until 12).joinToString("") { c -> ((r + c) % 12).toString(36) } }
        val d = draft(12, *rows.toTypedArray())
        assertEquals(NotationResult.Ok(d), parse(blanked(QueensNotation.print(d))))
    }

    // --- fixtures ---------------------------------------------------------------------------

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/notation/$name")!!.readBytes().decodeToString()

    @Test
    fun `fixtures parse, print canonically and match their expect line`() {
        for (name in listOf("queens-unique-5.txt", "queens-multiple-5.txt", "queens-incomplete-5.txt")) {
            val text = fixture(name)
            val expect = text.lineSequence().first { it.startsWith("# expect:") }
                .removePrefix("# expect:").trim().split(" ")
            val wantedVerdict = expect.first()
            val wantedBlocks = expect.drop(1)

            val parsed = parse(blanked(*text.lines().toTypedArray()))
            assertTrue("$name should parse", parsed is NotationResult.Ok)
            val d = (parsed as NotationResult.Ok).draft

            assertEquals("$name print not canonical",
                text.trimEnd('\n').lines().filterNot { it.startsWith("#") }.joinToString("\n") + "\n",
                QueensNotation.print(d))

            val verdict = QueensEditor.analyze(d, Budget.Unlimited)
            assertEquals("$name verdict", wantedVerdict, verdict::class.simpleName!!.lowercase())
            val blockIds = QueensEditor.publishBlocks(d, verdict).map { it.id }
            assertEquals("$name blocks", wantedBlocks, blockIds)
        }
    }

    @Test
    fun `editor exposes notation version and columns`() {
        assertEquals(1, QueensEditor.notationVersion)
        val d = draft(5, "00111", "02211", "02333", "44433", "44443")
        assertEquals(5, QueensEditor.columns(d))
    }

    // --- errors -----------------------------------------------------------------------------

    @Test
    fun `short row errors at its line, col 1`() {
        // header blanked -> row 1 is text line 2; row 3 (line 4) has 4 cells
        val err = parse(blanked("", "00111", "02211", "0233", "44433", "44443"))
        assertEquals(NotationResult.Err(4, 1, "row 3 has 4 cells, expected 5"), err)
    }

    @Test
    fun `extra row errors at col 1 of the first extra row`() {
        val err = parse(blanked("", "00111", "02211", "02333", "44433", "44443", "44443"))
        assertEquals(NotationResult.Err(7, 1, "unexpected row 6 of 5"), err)
    }

    @Test
    fun `missing row errors at col 1 of the last present row`() {
        val err = parse(blanked("", "00111", "02211", "02333", "44433"))
        assertEquals(NotationResult.Err(5, 1, "missing row 5 of 5"), err)
    }

    @Test
    fun `bad character errors at its line and column`() {
        val err = parse(blanked("", "00111", "02x11", "02333", "44433", "44443"))
        assertEquals(NotationResult.Err(3, 3, "unexpected character 'x'"), err)
    }

    @Test
    fun `region id outside the board errors at its column`() {
        val err = parse(blanked("", "00111", "02211", "023b3", "44433", "44443"))
        assertEquals(NotationResult.Err(4, 4, "region id 11 outside 0 until 5"), err)
    }

    @Test
    fun `13-row board errors`() {
        val rows = List(13) { "a".repeat(13) }
        val err = parse(blanked("", *rows.toTypedArray()))
        assertTrue(err is NotationResult.Err)
        assertEquals(2, (err as NotationResult.Err).line)
        assertEquals(1, err.col)
    }

    @Test
    fun `4x12 sizes accepted, 3x13 rejected by size`() {
        val min = QueensVariants.all.minOf { it.size }
        val max = QueensVariants.all.maxOf { it.size }
        assertEquals(4, min)
        assertEquals(12, max)
        val err = parse(blanked("", "000", "111", "222"))
        assertTrue(err is NotationResult.Err)
    }

    @Test
    fun `unpainted cells survive as UNPAINTED`() {
        val ok = parse(blanked("", "0...", "...1", "..22", ".2.2")) as NotationResult.Ok
        assertTrue(QueensDraft.UNPAINTED in ok.draft.regions)
    }

    @Test
    fun `crowns never appear in notation`() {
        val d = draft(5, "00111", "02211", "02333", "44433", "44443").copy(crowns = setOf(0))
        val printed = QueensNotation.print(d)
        assertEquals(QueensNotation.print(d.copy(crowns = emptySet())), printed)
        assertTrue(parse(blanked(printed)) is NotationResult.Ok)
    }

    @Test
    fun `publish block ids cover split-region`() {
        // the expect lines in the fixtures only ever use board-level block ids
        assertTrue(PublishBlock.SPLIT_REGION in QueensPublishGates.blockIds)
        assertTrue(Verdict.Incomplete::class.simpleName!!.lowercase() == "incomplete")
    }
}

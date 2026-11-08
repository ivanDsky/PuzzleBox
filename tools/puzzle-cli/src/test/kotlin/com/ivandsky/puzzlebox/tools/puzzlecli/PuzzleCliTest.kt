package com.ivandsky.puzzlebox.tools.puzzlecli

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `validate` behaviour at the command seam (M5-03 DoD): usage errors, unreadable files,
 * malformed text, unknown games, unsupported versions, too-large text, and the verdict
 * fixtures under `games/queens/src/test/resources/notation/` (§9.3). Every case asserts
 * the exit code; every JSON case asserts the printed verdict line.
 */
class PuzzleCliTest {

    /** Records what run() printed; [out] lines and [err] messages stay separate. */
    private class Io {
        val outLines = mutableListOf<String>()
        val errLines = mutableListOf<String>()
        val out: (String) -> Unit = { outLines.add(it) }
        val err: (String) -> Unit = { errLines.add(it) }
    }

    private fun fixture(name: String): String =
        File(System.getProperty("notation.fixtures"), "games/queens/src/test/resources/notation/$name")
            .readText()

    private fun verdictOf(io: Io): CliVerdict {
        assertEquals("one JSON line expected, got $io", 1, io.outLines.size)
        return Json.decodeFromString(CliVerdict.serializer(), io.outLines.single())
    }

    @Test
    fun `no args is usage`() {
        val io = Io()
        val exit = PuzzleCli.run(emptyList(), { "" }, io.out, io.err)
        assertEquals(CliExit.USAGE, exit)
        assertTrue(io.outLines.isEmpty())
        assertTrue(io.errLines.isNotEmpty())
    }

    @Test
    fun `solve unique fixture carries the solution values and exits publishable`() {
        val io = Io()
        val exit = PuzzleCli.run(
            listOf("solve", "board.txt"),
            { fixture("queens-unique-5.txt") },
            io.out,
            io.err,
        )
        assertEquals(CliExit.PUBLISHABLE, exit)
        val verdict = verdictOf(io)
        assertEquals("unique", verdict.verdict)
        assertTrue("solution expected: $verdict", verdict.solution != null && verdict.solution.size == 5)
    }

    @Test
    fun `solve multiple fixture leaves solution null and is not publishable`() {
        val io = Io()
        val exit = PuzzleCli.run(
            listOf("solve", "board.txt"),
            { fixture("queens-multiple-5.txt") },
            io.out,
            io.err,
        )
        assertEquals(CliExit.NOT_PUBLISHABLE, exit)
        val verdict = verdictOf(io)
        assertEquals("multiple", verdict.verdict)
        assertNull(verdict.solution)
    }

    @Test
    fun `validate unique fixture never carries a solution`() {
        val io = Io()
        val exit = PuzzleCli.run(
            listOf("validate", "board.txt"),
            { fixture("queens-unique-5.txt") },
            io.out,
            io.err,
        )
        assertEquals(CliExit.PUBLISHABLE, exit)
        assertNull(verdictOf(io).solution)
    }

    @Test
    fun `unknown command is usage`() {
        val io = Io()
        val exit = PuzzleCli.run(listOf("frobnicate", "board.txt"), { "" }, io.out, io.err)
        assertEquals(CliExit.USAGE, exit)
        assertTrue(io.outLines.isEmpty())
    }

    @Test
    fun `unreadable file is usage`() {
        val io = Io()
        val exit = PuzzleCli.run(listOf("validate", "board.txt"), { throw java.io.IOException("boom") }, io.out, io.err)
        assertEquals(CliExit.USAGE, exit)
        assertTrue(io.outLines.isEmpty())
    }

    @Test
    fun `header-less text is malformed invalid text`() {
        val io = Io()
        val text = "00001\n00031\n"
        val exit = PuzzleCli.run(listOf("validate", "board.txt"), { text }, io.out, io.err)
        assertEquals(CliExit.INVALID_TEXT, exit)
        val verdict = verdictOf(io)
        assertEquals("malformed", verdict.verdict)
        assertEquals("", verdict.game)
        assertEquals(1, verdict.errors.size)
        assertEquals(1, verdict.errors[0].line)
    }

    @Test
    fun `unknown game is invalid text`() {
        val io = Io()
        val text = "puzzlebox nosuch v1\n"
        val exit = PuzzleCli.run(listOf("validate", "board.txt"), { text }, io.out, io.err)
        assertEquals(CliExit.INVALID_TEXT, exit)
        val verdict = verdictOf(io)
        assertEquals("malformed", verdict.verdict)
        assertEquals("nosuch", verdict.game)
        assertTrue(verdict.errors[0].message.contains("nosuch"))
    }

    @Test
    fun `unsupported version names the highest known`() {
        val io = Io()
        val text = "puzzlebox queens v9\n00111\n02211\n02333\n44433\n44442\n"
        val exit = PuzzleCli.run(listOf("validate", "board.txt"), { text }, io.out, io.err)
        assertEquals(CliExit.INVALID_TEXT, exit)
        val verdict = verdictOf(io)
        assertEquals("malformed", verdict.verdict)
        assertEquals("queens", verdict.game)
        assertTrue("message must name v1: ${verdict.errors[0].message}", verdict.errors[0].message.contains("v1"))
    }

    @Test
    fun `too-large text is malformed at the first past-cap position`() {
        val io = Io()
        val header = "puzzlebox queens v1\n"
        val body = "1".repeat(70 * 1024)
        val exit = PuzzleCli.run(listOf("validate", "board.txt"), { header + body }, io.out, io.err)
        assertEquals(CliExit.INVALID_TEXT, exit)
        val verdict = verdictOf(io)
        assertEquals("malformed", verdict.verdict)
        assertTrue(verdict.errors[0].message.contains("65536"))
        assertTrue(verdict.errors[0].line > 0 && verdict.errors[0].col > 0)
    }

    @Test
    fun `unique fixture with no blocks is publishable`() {
        val io = Io()
        val exit = PuzzleCli.run(
            listOf("validate", "board.txt"),
            { fixture("queens-unique-5.txt") },
            io.out,
            io.err,
        )
        assertEquals(CliExit.PUBLISHABLE, exit)
        val verdict = verdictOf(io)
        assertEquals("queens", verdict.game)
        assertEquals("unique", verdict.verdict)
        assertEquals(1, verdict.schema)
        assertTrue(verdict.blocks.isEmpty())
        assertTrue(verdict.needsGuessing == true || verdict.needsGuessing == false)
        assertNull(verdict.solution)
    }

    @Test
    fun `multiple fixture is not publishable in 1-based coordinates`() {
        val io = Io()
        val exit = PuzzleCli.run(
            listOf("validate", "board.txt"),
            { fixture("queens-multiple-5.txt") },
            io.out,
            io.err,
        )
        assertEquals(CliExit.NOT_PUBLISHABLE, exit)
        val verdict = verdictOf(io)
        assertEquals("multiple", verdict.verdict)
        assertTrue(verdict.differingCells.isNotEmpty())
        for (cell in verdict.differingCells) {
            assertEquals(2, cell.size)
            assertTrue("1-based [row, col] expected: $cell", cell[0] in 1..5 && cell[1] in 1..5)
        }
    }
}

package com.ivandsky.puzzlebox.tools.puzzlecli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps `docs/puzzle-notation.md` honest both ways (M5-15-a, spec A23): every ```puzzlebox
 * block is byte-equal to one fixture file under a game's `src/test/resources/notation/`,
 * every fixture file appears in the doc exactly once, and each block sits in the section of
 * the game it is written for. Every ```json block is tagged `json <fixture> <validate|solve>`
 * and equals the line `PuzzleCli` prints for that command on that fixture, so no sample or
 * number in the doc can drift from the tool. Fixtures come from [NotationFixtures], the
 * loader every parity test shares.
 */
class NotationDocTest {

    private val doc: List<String> by lazy {
        File(System.getProperty("notation.fixtures"), "docs/puzzle-notation.md").readLines()
    }

    @Test
    fun `every puzzlebox block is byte-equal to a fixture file`() {
        val texts = NotationFixtures.all().map { it.text }.toSet()
        val strays = fences(PUZZLEBOX).filter { it.text !in texts }
        assertTrue(
            "blocks that match no fixture file (line of the opening fence): " +
                strays.joinToString { "${it.fenceLine}: ${it.text.lineSequence().drop(1).first()}" },
            strays.isEmpty(),
        )
    }

    @Test
    fun `every fixture file appears in the doc exactly once`() {
        val shown = fences(PUZZLEBOX).map { it.text }
        for (fixture in NotationFixtures.all()) {
            val count = shown.count { it == fixture.text }
            assertEquals("${fixture.file.path} appears $count times in the doc", 1, count)
        }
    }

    @Test
    fun `each block sits under the section of its own game id`() {
        for (block in fences(PUZZLEBOX)) {
            val gameId = block.text.lineSequence().first { it.startsWith("puzzlebox ") }.split(" ")[1]
            assertTrue(
                "block at line ${block.fenceLine} (puzzlebox $gameId) is under \"${block.section}\"",
                block.section.contains("`$gameId`"),
            )
        }
    }

    @Test
    fun `every json block is the line puzzle-cli prints for its tagged fixture and command`() {
        val blocks = fences(JSON)
        assertTrue("the doc has no json blocks", blocks.isNotEmpty())
        for (block in blocks) {
            val args = block.info.removePrefix(JSON).trim().split(" ").filter { it.isNotEmpty() }
            assertTrue(
                "json block at line ${block.fenceLine} must be tagged `json <fixture> <validate|solve>`",
                args.size == 2 && args[1] in COMMANDS,
            )
            val fixture = NotationFixtures.all().singleOrNull { it.file.nameWithoutExtension == args[0] }
            assertTrue("json block at line ${block.fenceLine}: no fixture named ${args[0]}", fixture != null)
            val out = mutableListOf<String>()
            val err = mutableListOf<String>()
            PuzzleCli.run(listOf(args[1], "board.txt"), { fixture!!.text }, out::add, err::add)
            assertEquals("json block at line ${block.fenceLine} (${args.joinToString(" ")})", out.single() + "\n", block.text)
        }
    }

    @Test
    fun `the doc has a section per game id and lists all four exit codes`() {
        val headings = doc.filter { it.startsWith("## ") }
        for (gameId in listOf("queens", "zip", "tango", "minisudoku", "sudoku", "strands")) {
            assertTrue("no `## ` section names `$gameId`", headings.any { it.contains("`$gameId`") })
        }
        val codes = listOf(CliExit.PUBLISHABLE, CliExit.NOT_PUBLISHABLE, CliExit.INVALID_TEXT, CliExit.USAGE)
        for (code in codes) {
            assertTrue("no table row for exit code $code", doc.any { it.startsWith("| `$code` |") })
        }
    }

    /** One fenced block: its info string, the `## ` section it sits in, and its text ("\n"-ended lines). */
    private data class DocFence(val fenceLine: Int, val info: String, val section: String, val text: String)

    /** Every fence whose info string starts with [tag], in document order. */
    private fun fences(tag: String): List<DocFence> {
        val found = mutableListOf<DocFence>()
        var section = ""
        var open: DocFence? = null
        val body = StringBuilder()
        for ((index, line) in doc.withIndex()) {
            val current = open
            when {
                current != null && line == FENCE -> {
                    found += current.copy(text = body.toString())
                    body.clear()
                    open = null
                }
                current != null -> body.append(line).append('\n')
                line.startsWith(FENCE) -> open = DocFence(index + 1, line.removePrefix(FENCE), section, "")
                line.startsWith("## ") -> section = line
            }
        }
        assertTrue("a fenced block is never closed", open == null)
        return found.filter { it.info == tag || it.info.startsWith("$tag ") }
    }

    private companion object {
        const val FENCE = "```"
        const val PUZZLEBOX = "puzzlebox"
        const val JSON = "json"
        val COMMANDS = listOf("validate", "solve")
    }
}

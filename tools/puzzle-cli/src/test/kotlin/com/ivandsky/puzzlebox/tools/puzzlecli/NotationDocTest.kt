package com.ivandsky.puzzlebox.tools.puzzlecli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps `docs/puzzle-notation.md` honest both ways (M5-15-a, spec A23): every ```puzzlebox
 * block is byte-equal to one fixture file under a game's `src/test/resources/notation/`,
 * every fixture file appears in the doc exactly once, and each block sits in the section of
 * the game it is written for. Fixtures come from [NotationFixtures], the loader every parity
 * test shares.
 */
class NotationDocTest {

    private val doc: List<String> by lazy {
        File(System.getProperty("notation.fixtures"), "docs/puzzle-notation.md").readLines()
    }

    @Test
    fun `every puzzlebox block is byte-equal to a fixture file`() {
        val texts = NotationFixtures.all().map { it.text }.toSet()
        val strays = blocks().filter { it.text !in texts }
        assertTrue(
            "blocks that match no fixture file (line of the opening fence): " +
                strays.joinToString { "${it.fenceLine}: ${it.text.lineSequence().drop(1).first()}" },
            strays.isEmpty(),
        )
    }

    @Test
    fun `every fixture file appears in the doc exactly once`() {
        val shown = blocks().map { it.text }
        for (fixture in NotationFixtures.all()) {
            val count = shown.count { it == fixture.text }
            assertEquals("${fixture.file.path} appears $count times in the doc", 1, count)
        }
    }

    @Test
    fun `each block sits under the section of its own game id`() {
        for (block in blocks()) {
            val gameId = block.text.lineSequence().first { it.startsWith("puzzlebox ") }.split(" ")[1]
            assertTrue(
                "block at line ${block.fenceLine} (puzzlebox $gameId) is under \"${block.section}\"",
                block.section.contains("`$gameId`"),
            )
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

    /** One ```puzzlebox block: its text (lines between the fences, each ended by "\n"). */
    private data class DocBlock(val fenceLine: Int, val section: String, val text: String)

    private fun blocks(): List<DocBlock> {
        val found = mutableListOf<DocBlock>()
        var section = ""
        var open: Pair<Int, MutableList<String>>? = null
        for ((index, line) in doc.withIndex()) {
            val current = open
            when {
                current != null && line == FENCE -> {
                    val text = current.second.joinToString("") { "$it\n" }
                    found += DocBlock(current.first, section, text)
                    open = null
                }
                current != null -> current.second += line
                line == "$FENCE$TAG" -> open = (index + 1) to mutableListOf()
                line.startsWith("## ") -> section = line
            }
        }
        assertTrue("a ```puzzlebox block is never closed", open == null)
        return found
    }

    private companion object {
        const val FENCE = "```"
        const val TAG = "puzzlebox"
    }
}

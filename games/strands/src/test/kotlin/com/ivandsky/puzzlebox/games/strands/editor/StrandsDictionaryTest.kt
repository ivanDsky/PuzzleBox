package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.games.strands.StrandsFixtures
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsDictionaryTest {
    private fun resource(name: String) =
        checkNotNull(StrandsDictionaryTest::class.java.getResourceAsStream("/editor/$name")) { "no resource $name" }

    private fun blocklist() = resource("blocklist.txt").bufferedReader().readLines().filter { it.isNotBlank() }

    private fun rawEnable() = GZIPInputStream(resource("enable.txt.gz")).bufferedReader().readLines()

    private fun board(vararg rows: String) =
        List(StrandsDraft.ROWS) { rows.getOrElse(it) { "" }.padEnd(StrandsDraft.COLS, StrandsDraft.BLANK) }.joinToString("")

    @Test
    fun `a known word is in, a non-word and a three-letter word are out`() {
        assertTrue(StrandsDictionary.contains("APPLE"))
        assertFalse(StrandsDictionary.contains("QZXVB"))
        assertFalse(StrandsDictionary.contains("CAT"))
    }

    @Test
    fun `hasPrefix answers for the start of a word and nothing else`() {
        assertTrue(StrandsDictionary.hasPrefix("APPL"))
        assertTrue(StrandsDictionary.hasPrefix("APPLE"))
        assertTrue(StrandsDictionary.hasPrefix("A"))
        assertFalse(StrandsDictionary.hasPrefix("QZX"))
        assertFalse(StrandsDictionary.hasPrefix("ZZZZZZ"))
    }

    @Test
    fun `a blocklisted word is out of the dictionary and flagged`() {
        val blocked = blocklist()
        assertTrue("the bundled list holds blocklisted words to subtract", rawEnable().any { it in blocked })

        blocked.forEach {
            assertTrue(it, StrandsDictionary.isBlocklisted(it))
            assertFalse(it, StrandsDictionary.contains(it))
        }
        assertFalse(StrandsDictionary.isBlocklisted("APPLE"))
    }

    @Test
    fun `the bundled list is under 600 KB, at least four letters, uppercase and sorted`() {
        assertTrue(resource("enable.txt.gz").use { it.readBytes().size } < 600_000)

        val words = rawEnable()
        assertTrue(words.all { word -> word.length >= 4 && word.all { it in 'A'..'Z' } })
        assertEquals(words.sorted(), words)
    }

    @Test
    fun `a word traces through all eight neighbours but never across a row end`() {
        val diagonal = board("T", ".R", "..E", "...E")
        val wrapped = board(".....T", "REE")

        assertTrue("TREE" in StrandsValidWords.of(diagonal, emptySet()))
        assertFalse("TREE" in StrandsValidWords.of(wrapped, emptySet()))
    }

    @Test
    fun `a word that needs a cell twice is not found`() {
        val once = board("NO")
        val twice = board("NO", "NO")

        assertFalse("NOON" in StrandsValidWords.of(once, emptySet()))
        assertTrue("NOON" in StrandsValidWords.of(twice, emptySet()))
    }

    @Test
    fun `extra words join the set even when not traceable`() {
        assertEquals(setOf("QZXVB"), StrandsValidWords.of(board(), setOf("QZXVB")))
    }

    @Test
    fun `a shipped daily finds its theme words and every valid word the dictionary holds`() {
        val puzzle = StrandsFixtures.puzzle(LocalDate.of(2026, 11, 6))
        val themeWords = puzzle.themeWords.map { it.text }.toSet() + puzzle.spangram.text

        val found = StrandsValidWords.of(puzzle.letters, themeWords)

        assertTrue(found.containsAll(themeWords))
        val known = puzzle.validWords.filter { StrandsDictionary.contains(it) }
        assertEquals(emptyList<String>(), known.filterNot { it in found })
    }
}

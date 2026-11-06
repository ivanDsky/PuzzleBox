package com.ivandsky.puzzlebox.games.strands.feed

import com.ivandsky.puzzlebox.games.strands.StrandsFixtures
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every committed real NYT day parses, passes the gate and builds a playable puzzle (SC2). */
class StrandsRealFixturesTest {

    private val days: List<LocalDate> =
        File(checkNotNull(javaClass.getResource("/fixtures")).toURI()).listFiles()!!
            .filter { it.extension == "json" } // session-codec .bin fixtures share the directory
            .map { LocalDate.parse(it.nameWithoutExtension) }
            .sorted()

    @Test
    fun `the committed days cover the edge cases`() {
        assertTrue(days.size >= 10)
        listOf("2024-03-04", "2024-11-08", "2024-12-31", "2025-01-01", "2025-09-13", "2026-11-05")
            .forEach { assertTrue("missing fixture $it", LocalDate.parse(it) in days) }
    }

    @Test
    fun `every committed day is trusted and builds its puzzle`() {
        days.forEach { day ->
            val puzzle = StrandsFixtures.puzzle(day)
            assertEquals("$day", puzzle.rows * puzzle.cols, puzzle.letters.length)
            assertTrue("$day", puzzle.validWords.containsAll(puzzle.themeWords.map { it.text } + puzzle.spangram.text))
        }
    }

    @Test
    fun `an emoji clue survives verbatim`() {
        assertEquals("👋👏🤝🤏✌️☝️✊🙏", StrandsFixtures.puzzle(LocalDate.of(2024, 11, 8)).clue)
    }

    @Test
    fun `a spangram missing from solutions is still a valid word`() {
        val day = LocalDate.of(2024, 10, 7)
        assertTrue("\"THATSCOOL\"" !in StrandsFixtures.json(day).substringAfter("\"solutions\"").substringBefore("]"))
        assertTrue("THATSCOOL" in StrandsFixtures.puzzle(day).validWords)
    }

    @Test
    fun `a day with over 1900 solutions keeps them all`() {
        assertTrue(StrandsFixtures.puzzle(LocalDate.of(2025, 9, 13)).validWords.size >= 1900)
    }
}

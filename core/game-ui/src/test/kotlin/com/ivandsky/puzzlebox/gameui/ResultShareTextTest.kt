package com.ivandsky.puzzlebox.gameui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The share line (spec A17) carries only the puzzle, the time and the hints: no link and
 * nothing about the board, so a shared result never spoils the puzzle.
 */
class ResultShareTextTest {

    private val daily = resultShareText("Queens", 918, 58_000, 0)

    @Test
    fun `the share text carries no link`() {
        listOf("http", "://", "www.").forEach { assertFalse("no $it in \"$daily\"", daily.contains(it)) }
    }

    @Test
    fun `the share text is only the puzzle, the time and the hints`() {
        // No board: the line is exactly its three parts, nothing more.
        val parts = daily.split(" · ")
        assertEquals(3, parts.size)
        assertEquals(1, daily.lines().size)
        assertEquals(listOf("Queens", "#918"), parts[0].split(" ").drop(1))
        assertEquals("0:58", parts[1])
    }

    @Test
    fun `a practice puzzle has no edition number`() {
        val practice = resultShareText("Tango", null, 72_000, 1)
        assertFalse(practice.contains("#"))
        assertEquals("1:12", practice.split(" · ")[1])
    }
}

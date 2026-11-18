package com.ivandsky.puzzlebox.gameui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The share text's header carries only the puzzle, the time and the hints; the optional strip and link are extra
 * lines, so a shared result never shows the board.
 */
class ResultShareTextTest {

    private val daily = resultShareText("Queens", 918, 58_000, 0)

    @Test
    fun `without a link or strip the text is today's header byte for byte`() {
        assertEquals("PuzzleBox Zip #608 · 3:03 · 1 hint", resultShareText("Zip", 608, 183_000, 1))
        assertEquals("PuzzleBox Zip #608 · 3:03 · 1 hint", resultShareText("Zip", 608, 183_000, 1, link = null, strip = null))
    }

    @Test
    fun `the header has no link`() {
        listOf("http", "://", "www.").forEach { assertFalse("no $it in \"$daily\"", daily.contains(it)) }
    }

    @Test
    fun `a strip adds a second line and a link adds the last line`() {
        val header = "PuzzleBox Zip #608 · 3:03 · 1 hint"
        val strip = "🟥⬜⬜⬜⬜⬜⬜⬜⬜⬜"
        val link = "https://puzzlebox-f3356.web.app/p/zip/2026-11-18"
        assertEquals("$header\n$strip", resultShareText("Zip", 608, 183_000, 1, strip = strip))
        assertEquals("$header\n$link", resultShareText("Zip", 608, 183_000, 1, link = link))
        assertEquals("$header\n$strip\n$link", resultShareText("Zip", 608, 183_000, 1, link = link, strip = strip))
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

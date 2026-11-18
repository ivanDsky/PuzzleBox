package com.ivandsky.puzzlebox.profile.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewParserTest {

    @Test
    fun `one release keeps its version, date and notes in order`() {
        val text = "## 0.36.2 2026-11-18\n- First.\n- Second.\n"

        assertEquals(
            listOf(WhatsNewRelease("0.36.2", "2026-11-18", listOf("First.", "Second."))),
            WhatsNewParser.parse(text),
        )
    }

    @Test
    fun `several releases come back in file order, not sorted`() {
        val text = "## 0.30.0 2026-10-01\n- Old.\n\n## 0.36.2 2026-11-18\n- New.\n\n## 0.31.0 2026-10-05\n- Middle.\n"

        assertEquals(listOf("0.30.0", "0.36.2", "0.31.0"), WhatsNewParser.parse(text).map { it.version })
    }

    @Test
    fun `comments and blank lines are ignored`() {
        val text = "# header comment\n\n## 0.36.2 2026-11-18\n# between\n- One.\n\n   \n- Two.\n"

        assertEquals(listOf("One.", "Two."), WhatsNewParser.parse(text).single().notes)
    }

    @Test
    fun `an empty file is no releases`() {
        assertEquals(emptyList<WhatsNewRelease>(), WhatsNewParser.parse(""))
        assertEquals(emptyList<WhatsNewRelease>(), WhatsNewParser.parse("# only a comment\n\n"))
    }

    @Test
    fun `a release with no notes is an error at its own line`() {
        assertFormatError("## 0.36.2 2026-11-18\n- One.\n\n## 0.36.1 2026-11-17\n\n## 0.36.0 2026-11-16\n- Two.\n", line = 4)
        assertFormatError("## 0.36.2 2026-11-18\n", line = 1)
    }

    @Test
    fun `a bad version is an error`() {
        assertFormatError("## 0.36 2026-11-18\n- One.\n", line = 1)
        assertFormatError("## v0.36.2 2026-11-18\n- One.\n", line = 1)
        assertFormatError("## 0.36.2-beta 2026-11-18\n- One.\n", line = 1)
    }

    @Test
    fun `a bad date is an error`() {
        assertFormatError("## 0.36.2 2026-13-18\n- One.\n", line = 1)
        assertFormatError("## 0.36.2 2026-11-00\n- One.\n", line = 1)
        assertFormatError("## 0.36.2 2026-11-32\n- One.\n", line = 1)
        assertFormatError("## 0.36.2 18-11-2026\n- One.\n", line = 1)
        assertFormatError("## 0.36.2\n- One.\n", line = 1)
    }

    @Test
    fun `a date that is not on the calendar still parses`() {
        assertEquals("2026-02-31", WhatsNewParser.parse("## 0.36.2 2026-02-31\n- One.\n").single().date)
    }

    @Test
    fun `an unknown line is an error with its line number`() {
        assertFormatError("## 0.36.2 2026-11-18\n- One.\nsome prose\n", line = 3)
        assertFormatError("## 0.36.2 2026-11-18\n* One.\n", line = 2)
    }

    @Test
    fun `a note before any release or without text is an error`() {
        assertFormatError("# c\n- Stray.\n## 0.36.2 2026-11-18\n- One.\n", line = 2)
        assertFormatError("## 0.36.2 2026-11-18\n- \n", line = 2)
    }

    @Test
    fun `windows line endings parse like unix ones`() {
        val releases = WhatsNewParser.parse("## 0.36.2 2026-11-18\r\n- One.\r\n")

        assertEquals(listOf("One."), releases.single().notes)
    }

    private fun assertFormatError(text: String, line: Int) {
        val error = assertThrows(IllegalArgumentException::class.java) { WhatsNewParser.parse(text) }

        assertTrue(error.message, error.message!!.startsWith("whats_new line $line:"))
    }
}

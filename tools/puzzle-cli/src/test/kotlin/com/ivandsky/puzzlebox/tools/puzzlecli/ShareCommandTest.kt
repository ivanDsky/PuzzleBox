package com.ivandsky.puzzlebox.tools.puzzlecli

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/** `share` prints the share payload the app would publish, only for a board that is publishable. */
class ShareCommandTest {

    private fun fixture(game: String, name: String): String =
        File(System.getProperty("notation.fixtures"), "games/$game/src/test/resources/notation/$name").readText()

    private fun share(text: String): Triple<Int, List<String>, List<String>> {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val exit = PuzzleCli.runShare(listOf("board.txt"), { text }, { out.add(it) }, { err.add(it) })
        return Triple(exit, out, err)
    }

    @Test
    fun `a unique board prints its variant, codec version and base64 payload`() {
        val (exit, out, _) = share(fixture("queens", "queens-unique-5.txt"))
        assertEquals(CliExit.PUBLISHABLE, exit)
        val line = Json.decodeFromString(ShareLine.serializer(), out.single())
        assertEquals("queens", line.game)
        assertEquals("queens/n5", line.variant)
        assertTrue(line.shareCodecVersion >= 1)
        assertTrue(Base64.getDecoder().decode(line.payload).isNotEmpty())
    }

    @Test
    fun `a board with more than one solution prints nothing and is not publishable`() {
        val (exit, out, _) = share(fixture("queens", "queens-multiple-5.txt"))
        assertEquals(CliExit.NOT_PUBLISHABLE, exit)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `malformed text is invalid text`() {
        val (exit, out, _) = share("00001\n")
        assertEquals(CliExit.INVALID_TEXT, exit)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `the strands fixture shares with its clue`() {
        val (exit, out, _) = share(fixture("strands", "strands-unique.txt"))
        assertEquals(CliExit.PUBLISHABLE, exit)
        assertEquals("strands/nyt", Json.decodeFromString(ShareLine.serializer(), out.single()).variant)
    }
}

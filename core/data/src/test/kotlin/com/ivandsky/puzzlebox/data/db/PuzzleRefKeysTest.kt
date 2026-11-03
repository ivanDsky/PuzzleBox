package com.ivandsky.puzzlebox.data.db

import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The composite record identity (gameId + day/puzzleNumber) round-trips through one key form. */
class PuzzleRefKeysTest {

    private val day = LocalDate.of(2026, 10, 31)

    @Test
    fun `numbered ref encodes and decodes symmetrically`() {
        val ref = ZipPuzzleRef(day, 592)
        assertEquals("zip/2026-10-31/592", PuzzleRefKeys.encode(ref))
        assertEquals(ref, PuzzleRefKeys.decode("zip/2026-10-31/592"))
    }

    @Test
    fun `unnumbered ref encodes and decodes symmetrically`() {
        val ref = ZipPuzzleRef(day, puzzleNumber = null)
        assertEquals("zip/2026-10-31", PuzzleRefKeys.encode(ref))
        assertEquals(ref, PuzzleRefKeys.decode("zip/2026-10-31"))
    }

    @Test
    fun `queens refs encode and decode symmetrically under their own game id`() {
        val numbered = QueensPuzzleRef(day, 42)
        assertEquals("queens/2026-10-31/42", PuzzleRefKeys.encode(numbered))
        assertEquals(numbered, PuzzleRefKeys.decode("queens/2026-10-31/42"))

        val unnumbered = QueensPuzzleRef(day, puzzleNumber = null)
        assertEquals("queens/2026-10-31", PuzzleRefKeys.encode(unnumbered))
        assertEquals(unnumbered, PuzzleRefKeys.decode("queens/2026-10-31"))
    }

    @Test
    fun `tango refs encode and decode symmetrically (tango-05)`() {
        val numbered = com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef(day, 301)
        assertEquals("tango/2026-10-31/301", PuzzleRefKeys.encode(numbered))
        assertEquals(numbered, PuzzleRefKeys.decode("tango/2026-10-31/301"))

        val unnumbered = com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef(day, puzzleNumber = null)
        assertEquals("tango/2026-10-31", PuzzleRefKeys.encode(unnumbered))
        assertEquals(unnumbered, PuzzleRefKeys.decode("tango/2026-10-31"))
    }

    @Test
    fun `keys no registered game authored decode to null`() {
        assertNull(PuzzleRefKeys.decode("strands/2026-10-31/12"))
        assertNull(PuzzleRefKeys.decode("zip/not-a-date"))
        assertNull(PuzzleRefKeys.decode("zip/2026-10-31/not-a-number"))
        assertNull(PuzzleRefKeys.decode("zip/2026-10-31/592/extra"))
        assertNull(PuzzleRefKeys.decode("tango/not-a-date"))
    }
}

package com.ivandsky.puzzlebox.data.db

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
    fun `keys no registered game authored decode to null`() {
        assertNull(PuzzleRefKeys.decode("queens/2026-10-31/12"))
        assertNull(PuzzleRefKeys.decode("zip/not-a-date"))
        assertNull(PuzzleRefKeys.decode("zip/2026-10-31/not-a-number"))
        assertNull(PuzzleRefKeys.decode("zip/2026-10-31/592/extra"))
    }
}

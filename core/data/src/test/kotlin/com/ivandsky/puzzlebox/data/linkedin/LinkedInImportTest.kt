package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LinkedInImportTest {

    private val zip = GameId("zip")
    private val registry = TestGames.registry(listOf(zip))
    private val day = LocalDate.parse("2026-11-04")

    @Test
    fun `a solved result maps to a LinkedIn record`() {
        val record = LinkedInImport.toRecord(zip, day, 592, LinkedInOwnResult.Solved(83, hintFree = true), registry)

        assertNotNull(record)
        record!!
        assertEquals("zip/2026-11-04/592", PuzzleRefKeys.encode(record.puzzleRef))
        assertEquals(83_000L, record.time)
        assertEquals(0, record.hints)
        assertEquals(RecordStats(1, "{}"), record.stats)
        assertEquals(CompletionSource.LinkedIn, record.source)
        assertFalse(record.isRecord)
        assertNull(record.firstCompletedAt)
        assertNull(record.matchCode)
    }

    @Test
    fun `a result with hints maps to one hint`() {
        val record = LinkedInImport.toRecord(zip, day, 592, LinkedInOwnResult.Solved(83, hintFree = false), registry)

        assertEquals(1, record?.hints)
    }

    @Test
    fun `a game missing from the registry maps to null`() {
        val other = TestGames.registry(listOf(GameId("queens")))

        assertNull(LinkedInImport.toRecord(zip, day, 592, LinkedInOwnResult.Solved(83, hintFree = true), other))
    }
}

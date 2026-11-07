package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrandsScoreModelTest {

    private val ref = StrandsPuzzleRef(PuzzleKey.Daily(LocalDate.of(2026, 11, 5), 977))
    private val complete = StrandsState(tinyPuzzle, found = listOf("CAT", "ATS"), nonTheme = setOf("TAT"), hintsUsed = 2)

    @Test
    fun `a complete numbered day emits hints, time and the non-theme count`() {
        val record = StrandsScoreModel.completion(StrandsSession(ref, complete, elapsedMs = 61_000L))!!
        assertEquals(ref, record.puzzleRef)
        assertEquals(61_000L, record.time)
        assertEquals(2, record.hints)
        assertEquals(RecordStats(1, """{"v":1,"nonTheme":1}"""), record.stats)
    }

    @Test
    fun `incomplete or unnumbered play emits nothing`() {
        assertNull(StrandsScoreModel.completion(StrandsSession(ref, complete.copy(found = listOf("CAT")))))
        assertNull(StrandsScoreModel.completion(StrandsSession(StrandsPuzzleRef(PuzzleKey.Daily((ref.key as PuzzleKey.Daily).date, null)), complete)))
        assertNull(StrandsScoreModel.completion(StrandsSession(null, complete)))
    }

    @Test
    fun `a complete custom puzzle emits a record without a number`() {
        val custom = StrandsPuzzleRef(PuzzleKey.Custom("ABC234"))
        val record = StrandsScoreModel.completion(StrandsSession(custom, complete, elapsedMs = 61_000L))!!
        assertEquals(custom, record.puzzleRef)
        assertEquals(2, record.hints)
    }

    @Test
    fun `rating follows hints used`() {
        assertEquals(StrandsRating.Perfect, StrandsRating.of(0))
        assertEquals(StrandsRating.Great, StrandsRating.of(1))
        assertEquals(StrandsRating.WellDone, StrandsRating.of(2))
        assertEquals(StrandsRating.WellDone, StrandsRating.of(7))
    }
}

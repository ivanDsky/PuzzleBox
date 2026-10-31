package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Score model: A3 fields indexed, §10 counters in the ADR-0005 stats blob v1, custom boards emit nothing. */
class ZipScoreModelTest {

    private val model = ZipScoreModel()
    private val t0 = Instant.ofEpochMilli(1_000_000)

    private fun completedSession(ref: ZipPuzzleRef? = ZipPuzzleRef(java.time.LocalDate.of(2026, 10, 30), 592)) =
        ZipSession(
            ref = ref,
            puzzle = ZipTestSupport.n5Puzzle(),
            path = ZipTestSupport.n5Solution,
            hintsUsed = 3,
            mistakeCount = 5,
            backtrackCount = 2,
            elapsedMs = 61_500,
            timerStarted = true,
            completedAt = t0,
        )

    @Test
    fun `completion carries the indexed A3 fields`() {
        val record = model.completion(completedSession())!!
        assertEquals(ZipPuzzleRef(java.time.LocalDate.of(2026, 10, 30), 592), record.puzzleRef)
        assertEquals(61_500, record.time)
        assertEquals(3, record.hints)
        assertNull(record.firstCompletedAt) // core/data stamps it
        assertTrue(!record.isRecord)
    }

    @Test
    fun `stats blob is schema v1 with variant and counters`() {
        val blob = model.completion(completedSession())!!.stats.blob
        val json = Json.parseToJsonElement(blob).jsonObject
        assertEquals(1, json["v"]!!.jsonPrimitive.content.toInt())
        assertEquals("zip/n5", json["variant"]!!.jsonPrimitive.content)
        assertEquals(5, json["mistakes"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, json["backtracks"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, model.completion(completedSession())!!.stats.schemaVersion)
    }

    @Test
    fun `incomplete sessions emit nothing`() {
        val midPlay = completedSession().copy(completedAt = null, path = ZipTestSupport.path(0, 1))
        assertNull(model.completion(midPlay))
    }

    /** Custom boards have no identity yet (Phase 2): no ref, no record. */
    @Test
    fun `custom boards are not record-eligible`() {
        assertNull(model.completion(completedSession(ref = null)))
    }
}

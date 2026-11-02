package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The golden path (DoD): fixture → initial session → legal verdicts → completion →
 * CompletionRecord, plus the mistake counter and one-completion guard (A4).
 */
class TangoGoldenPathTest {

    private val now: Instant get() = Instant.ofEpochMilli(1_746_192_000_000)

    private fun freshSession(): TangoSession {
        val puzzle = (TangoDefinition.decode(TangoTestSupport.sampleBytes) as DecodeResult.Ok).value
        return (tangoPlugin.initialSession(puzzle) as DecodeResult.Ok).value
    }

    private fun playSolution(session: TangoSession): TangoSession {
        var s = session
        TangoTestSupport.solution.forEachIndexed { idx, cell ->
            if (idx !in TangoTestSupport.sample.givens) s = s.withMove(TangoMove(idx, cell), now)
        }
        return s
    }

    @Test
    fun `playing the documented solution completes the session`() {
        val completed = playSolution(freshSession())
        assertNotNull(completed.completedAt)
        assertTrue(completed.isComplete)
    }

    @Test
    fun `a completed session emits a record when a ref is present`() {
        val completed = playSolution(
            freshSession().copy(ref = TangoPuzzleRef(LocalDate.of(2026, 11, 2), 1), elapsedMs = 120_000, hintsUsed = 2),
        )
        val record = TangoScoreModel().completion(completed)
        assertNotNull(record)
        assertEquals(120_000L, record!!.time)
        assertEquals(2, record.hints)
        assertEquals(1, record.stats.schemaVersion)
    }

    @Test
    fun `an incomplete session emits no record`() {
        assertNull(TangoScoreModel().completion(freshSession()))
    }

    @Test
    fun `a custom board without a ref is not record-eligible`() {
        val completed = playSolution(freshSession())
        assertNull(TangoScoreModel().completion(completed))
    }

    @Test
    fun `an invalid move does not complete and counts as a mistake`() {
        val session = freshSession()
        val state = tangoPlugin.stateOf(session)
        val verdict = TangoRules.validate(TangoMove(0, TangoCell.MOON), state) // overwriting a given
        assertTrue(verdict is TangoVerdict.Rejected)
        val punished = session.withRejection()
        assertEquals(1, punished.mistakeCount)
        assertFalse(punished.isComplete)
    }

    @Test
    fun `completion is stamped exactly once`() {
        val completed = playSolution(freshSession())
        val replayed = completed.withMove(TangoMove(2, TangoCell.MOON), now.plusSeconds(60))
        assertEquals(completed.completedAt, replayed.completedAt)
    }

    @Test
    fun `hints are counted on the session`() {
        assertEquals(1, freshSession().withHintApplied().hintsUsed)
    }
}

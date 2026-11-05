package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec A10: "New best" only on a strictly lower SHOWN time of a first solve; replays never rank. */
class RecordVerdictTest {

    private fun timeFirst(previousBestMs: Long?, timeMs: Long, replay: Boolean) = RecordVerdict.of(
        previous = previousBestMs?.let { BestScore(it, hints = 0) },
        timeMs = timeMs,
        hints = 0,
        replay = replay,
        order = RankOrder.TimeThenHints,
    )

    @Test
    fun `no previous best is the first solve`() {
        assertEquals(RecordVerdict.FirstSolve, timeFirst(previousBestMs = null, timeMs = 95_000, replay = false))
    }

    @Test
    fun `a strictly lower time is a new best`() {
        assertEquals(RecordVerdict.NewBest, timeFirst(previousBestMs = 95_000, timeMs = 94_000, replay = false))
    }

    @Test
    fun `an equal time ties the best`() {
        assertEquals(RecordVerdict.TiedBest, timeFirst(previousBestMs = 95_000, timeMs = 95_000, replay = false))
    }

    @Test
    fun `a few milliseconds faster that shows the same time is a tie, not a new best`() {
        // Device case: 3.826 s after a 3.862 s best — both read 00:03 on the result.
        assertEquals(RecordVerdict.TiedBest, timeFirst(previousBestMs = 3_862, timeMs = 3_826, replay = false))
    }

    @Test
    fun `a higher time is not a new best`() {
        assertEquals(RecordVerdict.NotBest, timeFirst(previousBestMs = 95_000, timeMs = 120_000, replay = false))
    }

    @Test
    fun `a replay is a replay however fast, and with no best on record`() {
        assertEquals(RecordVerdict.Replay, timeFirst(previousBestMs = 95_000, timeMs = 3_000, replay = true))
        assertEquals(RecordVerdict.Replay, timeFirst(previousBestMs = null, timeMs = 3_000, replay = true))
    }
}

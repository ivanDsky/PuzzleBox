package com.ivandsky.puzzlebox.gameui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec A10: "New best" only on a strictly lower time; "First solve" when no best existed. */
class RecordVerdictTest {

    @Test
    fun `no previous best is the first solve`() {
        assertEquals(RecordVerdict.FirstSolve, RecordVerdict.of(previousBestMs = null, timeMs = 95_000))
    }

    @Test
    fun `a strictly lower time is a new best`() {
        assertEquals(RecordVerdict.NewBest, RecordVerdict.of(previousBestMs = 95_000, timeMs = 94_999))
    }

    @Test
    fun `an equal time is not a new best`() {
        assertEquals(RecordVerdict.NotBest, RecordVerdict.of(previousBestMs = 95_000, timeMs = 95_000))
    }

    @Test
    fun `a higher time is not a new best`() {
        assertEquals(RecordVerdict.NotBest, RecordVerdict.of(previousBestMs = 95_000, timeMs = 120_000))
    }
}

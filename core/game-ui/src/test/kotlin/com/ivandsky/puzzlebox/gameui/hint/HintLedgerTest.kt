package com.ivandsky.puzzlebox.gameui.hint

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec A4/A9: a new step key counts once, a counted key is free whatever happened in between (a
 * culprit's re-show is just the same key again), the count saturates at 255, nothing outlives
 * [HintLedger.reset].
 */
class HintLedgerTest {

    /** Callers pass their game's step data classes; equal steps must be one key. */
    private data class Step(val cell: Int, val name: String)

    private val a = Step(cell = 3, name = "single")
    private val b = Step(cell = 7, name = "pair")
    private val ledger = HintLedger<Step>()

    @Test
    fun `a new key counts once`() {
        assertEquals(1, ledger.press(a, used = 0))
    }

    @Test
    fun `the same key again is free, however often it is pressed`() {
        ledger.press(a, used = 0)
        assertEquals(1, ledger.press(a, used = 1))
        assertEquals(1, ledger.press(a, used = 1))
    }

    @Test
    fun `a second new key counts again`() {
        ledger.press(a, used = 0)
        assertEquals(2, ledger.press(b, used = 1))
    }

    @Test
    fun `a counted key stays free after an unrelated key was shown in between`() {
        ledger.press(a, used = 0)
        ledger.press(b, used = 1)
        assertEquals(2, ledger.press(a, used = 2))
    }

    @Test
    fun `a new key at one below the ceiling reaches the ceiling`() {
        assertEquals(255, ledger.press(a, used = 254))
    }

    @Test
    fun `a new key at the ceiling saturates without throwing and is remembered as counted`() {
        assertEquals(MAX_HINTS_USED, ledger.press(a, used = MAX_HINTS_USED))
        ledger.press(b, used = MAX_HINTS_USED)
        assertEquals(MAX_HINTS_USED, ledger.press(a, used = MAX_HINTS_USED))
    }

    @Test
    fun `reset forgets counted keys`() {
        ledger.press(a, used = 0)
        ledger.reset()
        assertEquals(1, ledger.press(a, used = 0))
    }

    @Test
    fun `equal data-class keys built separately are one key`() {
        ledger.press(Step(cell = 3, name = "single"), used = 0)
        assertEquals(1, ledger.press(Step(cell = 3, name = "single"), used = 1))
    }
}

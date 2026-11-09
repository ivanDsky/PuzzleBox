package com.ivandsky.puzzlebox.gameui.hint

import com.ivandsky.puzzlebox.gameui.hint.HintPress.Action.ERASE_CULPRIT
import com.ivandsky.puzzlebox.gameui.hint.HintPress.Action.SHOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spec A4/A9/A10: a new step key counts once, a counted key is free whatever happened in between,
 * a culprit's second press while shown erases for free, any input forgets the shown key but keeps
 * the counted ones, the count saturates at 255, nothing outlives [HintLedger.reset].
 */
class HintLedgerTest {

    /** Callers pass their game's step data classes; equal steps must be one key. */
    private data class Step(val cell: Int, val name: String)

    private val a = Step(cell = 3, name = "single")
    private val b = Step(cell = 7, name = "pair")
    private val ledger = HintLedger<Step>()

    @Test
    fun `a new key shows and counts once`() {
        assertEquals(HintPress(SHOW, used = 1), ledger.press(a, culprit = false, used = 0))
        assertEquals(a, ledger.shown)
    }

    @Test
    fun `the same key again shows for free`() {
        ledger.press(a, culprit = false, used = 0)
        assertEquals(HintPress(SHOW, used = 1), ledger.press(a, culprit = false, used = 1))
    }

    @Test
    fun `a second new key counts again`() {
        ledger.press(a, culprit = false, used = 0)
        assertEquals(HintPress(SHOW, used = 2), ledger.press(b, culprit = false, used = 1))
        assertEquals(b, ledger.shown)
    }

    @Test
    fun `a counted key stays free after an unrelated key was shown in between`() {
        ledger.press(a, culprit = false, used = 0)
        ledger.press(b, culprit = false, used = 1)
        assertEquals(HintPress(SHOW, used = 2), ledger.press(a, culprit = false, used = 2))
        assertEquals(a, ledger.shown)
    }

    @Test
    fun `a culprit shows and counts on press one, then erases for free on press two`() {
        assertEquals(HintPress(SHOW, used = 1), ledger.press(a, culprit = true, used = 0))
        assertEquals(HintPress(ERASE_CULPRIT, used = 1), ledger.press(a, culprit = true, used = 1))
    }

    @Test
    fun `a non-culprit key never erases even while it is shown`() {
        ledger.press(a, culprit = false, used = 0)
        assertEquals(a, ledger.shown)
        assertEquals(HintPress(SHOW, used = 1), ledger.press(a, culprit = false, used = 1))
        assertEquals(HintPress(SHOW, used = 1), ledger.press(a, culprit = false, used = 1))
    }

    @Test
    fun `a culprit that is counted but no longer shown does not erase`() {
        ledger.press(a, culprit = true, used = 0)
        ledger.press(b, culprit = false, used = 1)
        assertEquals(HintPress(SHOW, used = 2), ledger.press(a, culprit = true, used = 2))
    }

    @Test
    fun `a culprit pressed after an input shows for free, then erases on the next press`() {
        ledger.press(a, culprit = true, used = 0)
        ledger.inputMade()
        assertEquals(HintPress(SHOW, used = 1), ledger.press(a, culprit = true, used = 1))
        assertEquals(HintPress(ERASE_CULPRIT, used = 1), ledger.press(a, culprit = true, used = 1))
    }

    @Test
    fun `an input forgets the shown key and keeps the counted keys`() {
        ledger.press(a, culprit = false, used = 0)
        ledger.press(b, culprit = false, used = 1)
        ledger.inputMade()
        assertNull(ledger.shown)
        assertEquals(HintPress(SHOW, used = 2), ledger.press(a, culprit = false, used = 2))
        assertEquals(HintPress(SHOW, used = 2), ledger.press(b, culprit = false, used = 2))
    }

    @Test
    fun `nothing is shown before the first press`() {
        assertNull(ledger.shown)
    }

    @Test
    fun `the ceiling is 255`() {
        assertEquals(255, MAX_HINTS_USED)
    }

    @Test
    fun `a new key at one below the ceiling reaches the ceiling`() {
        assertEquals(HintPress(SHOW, used = 255), ledger.press(a, culprit = false, used = 254))
    }

    @Test
    fun `a new key at the ceiling saturates without throwing and is remembered as counted`() {
        assertEquals(HintPress(SHOW, used = MAX_HINTS_USED), ledger.press(a, culprit = false, used = MAX_HINTS_USED))
        assertEquals(a, ledger.shown)
        ledger.press(b, culprit = false, used = MAX_HINTS_USED)
        assertEquals(HintPress(SHOW, used = MAX_HINTS_USED), ledger.press(a, culprit = false, used = MAX_HINTS_USED))
    }

    @Test
    fun `a culprit at the ceiling still erases on its second press`() {
        ledger.press(a, culprit = true, used = MAX_HINTS_USED)
        assertEquals(HintPress(ERASE_CULPRIT, used = MAX_HINTS_USED), ledger.press(a, culprit = true, used = MAX_HINTS_USED))
    }

    @Test
    fun `reset forgets counted keys and the shown key`() {
        ledger.press(a, culprit = false, used = 0)
        ledger.reset()
        assertNull(ledger.shown)
        assertEquals(HintPress(SHOW, used = 1), ledger.press(a, culprit = false, used = 0))
    }

    @Test
    fun `reset forgets a shown culprit so its next press shows and counts`() {
        ledger.press(a, culprit = true, used = 0)
        ledger.reset()
        assertEquals(HintPress(SHOW, used = 1), ledger.press(a, culprit = true, used = 0))
    }

    @Test
    fun `equal data-class keys built separately are one key`() {
        ledger.press(Step(cell = 3, name = "single"), culprit = true, used = 0)
        assertEquals(
            HintPress(ERASE_CULPRIT, used = 1),
            ledger.press(Step(cell = 3, name = "single"), culprit = true, used = 1),
        )
    }
}

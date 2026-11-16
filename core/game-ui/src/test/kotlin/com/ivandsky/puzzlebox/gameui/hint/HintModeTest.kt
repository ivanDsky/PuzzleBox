package com.ivandsky.puzzlebox.gameui.hint

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The first-move rule rides the registered HintMode (nav-rethink B4, ADR-0025 §5): off unless a
// registration asks, and a game with no stepwise picker gets its own variant.
class HintModeTest {

    @Test
    fun `stepwise is not first-move unless asked`() {
        assertFalse(HintMode.Stepwise(Dispatchers.Unconfined).firstMove)
        assertTrue(HintMode.Stepwise(Dispatchers.Unconfined, firstMove = true).firstMove)
    }

    @Test
    fun `the first-move variant carries its compute dispatcher`() {
        val mode: HintMode = HintMode.FirstMove(Dispatchers.Unconfined)

        assertEquals(Dispatchers.Unconfined, (mode as HintMode.FirstMove).compute)
    }
}

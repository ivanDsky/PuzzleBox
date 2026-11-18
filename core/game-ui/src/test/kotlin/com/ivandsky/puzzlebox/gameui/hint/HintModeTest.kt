package com.ivandsky.puzzlebox.gameui.hint

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class HintModeTest {

    @Test
    fun `the stepwise mode carries its compute dispatcher`() {
        val mode: HintMode = HintMode.Stepwise(Dispatchers.Unconfined)

        assertEquals(Dispatchers.Unconfined, (mode as HintMode.Stepwise).compute)
    }
}

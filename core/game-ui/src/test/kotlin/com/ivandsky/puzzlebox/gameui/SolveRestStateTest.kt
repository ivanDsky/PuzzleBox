package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The solve's success buzz goes through [HapticFeedback], where the Vibration switch gates it. */
class SolveRestStateTest {
    private val buzzes = ArrayList<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            buzzes += hapticFeedbackType
        }
    }

    @Test
    fun `begin buzzes one Confirm when enabled`() = runTest {
        val rest = SolveRestState(enabled = true, haptics = haptics)

        val done = rest.begin(this)
        testScheduler.runCurrent()
        rest.skip()
        done.await()

        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes)
    }

    @Test
    fun `begin does not buzz when disabled`() = runTest {
        SolveRestState(enabled = false, haptics = haptics).begin(this).await()

        assertEquals(emptyList<HapticFeedbackType>(), buzzes)
    }
}

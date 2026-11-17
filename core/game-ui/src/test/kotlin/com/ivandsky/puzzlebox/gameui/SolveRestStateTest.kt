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
        val rest = SolveRestState(enabled = true, playFeel = false, haptics = haptics)

        val done = rest.begin(this)
        testScheduler.runCurrent()
        rest.skip()
        done.await()

        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes)
    }

    @Test
    fun `begin does not buzz when disabled`() = runTest {
        val rest = SolveRestState(enabled = false, playFeel = false, haptics = haptics)

        rest.begin(this).await()

        assertEquals(false, rest.confirms)
        assertEquals(emptyList<HapticFeedbackType>(), buzzes)
    }

    @Test
    fun `with PlayFeel begin buzzes one Confirm when disabled, and is over at once`() = runTest {
        val rest = SolveRestState(enabled = false, playFeel = true, haptics = haptics)

        rest.begin(this).await()

        assertEquals(true, rest.confirms)
        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes)
        assertEquals(false, rest.resting)
    }
}

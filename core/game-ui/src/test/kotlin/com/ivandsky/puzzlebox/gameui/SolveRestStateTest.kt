package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `begin buzzes one Confirm when disabled, and is over at once`() = runTest {
        val rest = SolveRestState(enabled = false, haptics = haptics)

        rest.begin(this).await()

        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes)
        assertEquals(false, rest.resting)
    }

    @Test
    fun `the rest is still on at 1699 ms and over at 1700 ms`() = runTest {
        val rest = SolveRestState(enabled = true, haptics = haptics)

        val done = rest.begin(this)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(1_699)
        testScheduler.runCurrent()
        assertFalse("still resting at 1699 ms", done.isCompleted)

        testScheduler.advanceTimeBy(1)
        testScheduler.runCurrent()
        assertTrue("over at 1700 ms", done.isCompleted)
    }
}

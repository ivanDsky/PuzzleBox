package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D1: a replay frame plays its motion only for a forward step while the replay plays. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplayStepDirectionTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a later frame steps forward while animating`() = assertTrue(stepsForward(animate = true, lastAtMs = 1_000, atMs = 2_000))

    @Test
    fun `a frame at the same position steps forward while animating`() = assertTrue(stepsForward(animate = true, lastAtMs = 1_000, atMs = 1_000))

    @Test
    fun `an earlier frame does not step forward`() = assertFalse(stepsForward(animate = true, lastAtMs = 2_000, atMs = 1_000))

    @Test
    fun `no frame steps forward unless animating`() {
        assertFalse(stepsForward(animate = false, lastAtMs = 1_000, atMs = 2_000))
        assertFalse(stepsForward(animate = false, lastAtMs = 1_000, atMs = 1_000))
        assertFalse(stepsForward(animate = false, lastAtMs = 2_000, atMs = 1_000))
    }

    @Test
    fun `the composable compares each frame with the one shown before it`() {
        val atMs = mutableStateOf(1_000L)
        val seen = mutableListOf<Boolean>()
        rule.setContent {
            seen += rememberStepsForward(ReplayBoardOptions(animate = true, showErrors = true), atMs.value)
        }
        rule.runOnIdle { atMs.value = 3_000 }
        rule.runOnIdle { atMs.value = 2_000 }
        rule.runOnIdle { atMs.value = 4_000 }
        rule.waitForIdle()

        // The first frame is the baseline; 3 000 is forward, 2 000 steps back, 4 000 is forward from 2 000.
        assertEquals(listOf(true, true, false, true), seen)
    }
}

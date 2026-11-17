package com.ivandsky.puzzlebox.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Vibration preference gates the platform haptics at the root, and a flip applies without recomposing the content. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class VibrationGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = ArrayList<HapticFeedbackType>()
    private val platform = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            calls += hapticFeedbackType
        }
    }
    private lateinit var haptics: HapticFeedback
    private var compositions = 0

    private fun show(preferences: FakeUserPreferencesRepository) {
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides platform) {
                VibrationGate(preferences) {
                    haptics = LocalHapticFeedback.current
                    SideEffect { compositions++ }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `on by default, calls pass`() {
        show(FakeUserPreferencesRepository())

        haptics.performHapticFeedback(HapticFeedbackType.Confirm)

        assertEquals(listOf(HapticFeedbackType.Confirm), calls)
    }

    @Test
    fun `off, the platform gets zero calls`() {
        show(FakeUserPreferencesRepository(vibration = false))

        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

        assertEquals(emptyList<HapticFeedbackType>(), calls)
    }

    @Test
    fun `a flip mid-composition applies to the same instance`() {
        val preferences = FakeUserPreferencesRepository()
        show(preferences)
        val before = haptics
        val composedBefore = compositions

        runBlocking { preferences.setVibration(false) }
        composeRule.waitForIdle()
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        assertEquals(emptyList<HapticFeedbackType>(), calls)

        runBlocking { preferences.setVibration(true) }
        composeRule.waitForIdle()
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)

        assertEquals(listOf(HapticFeedbackType.Confirm), calls)
        assertEquals("one gate, not rebuilt per flip", before, haptics)
        assertEquals("a flip does not recompose the content", composedBefore, compositions)
    }
}

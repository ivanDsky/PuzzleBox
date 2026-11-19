package com.ivandsky.puzzlebox.app.debug.haptics

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Smoke: the lab opens, lists a row per way to move the motor (only the primitives the motor has), and a tap plays it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class HapticsLabPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val vibrator = ApplicationProvider.getApplicationContext<Context>().getSystemService(Vibrator::class.java)
    private lateinit var view: View

    private fun open() {
        composeRule.setContent {
            view = LocalView.current
            PuzzleBoxTheme { Column(Modifier.verticalScroll(rememberScrollState())) { HapticsLabPanel() } }
        }
        composeRule.onNodeWithTag("debug.haptics.toggle").performClick()
    }

    @Test
    fun `opened, it lists every row kind and the motor's capabilities`() {
        shadowOf(vibrator).setSupportedPrimitives(listOf(VibrationEffect.Composition.PRIMITIVE_CLICK))
        open()

        listOf("P-TICK-T", "P-DOUBLE", "C-CLICK-0.6", "O-20-120", "O-35-120", "O-45-120", "W-30-100-30", "M-REJECT", "V-CLOCK_TICK", "V-KEYBOARD_TAP", "V-GESTURE_END")
            .forEach { composeRule.onNodeWithTag("debug.haptics.$it").assertExists() }
        composeRule.onNodeWithTag("debug.haptics.C-THUD-0.6").assertDoesNotExist()
        composeRule.onNodeWithText("hasAmplitudeControl", substring = true).assertExists()
    }

    @Test
    fun `a vibrator row plays as touch feedback and a View row plays its constant`() {
        open()

        composeRule.onNodeWithTag("debug.haptics.O-20-120").performScrollTo().performClick()
        val attributes = shadowOf(vibrator).vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_TOUCH, attributes.usage)
        assertEquals(listOf(20L), shadowOf(vibrator).pattern.toList())

        composeRule.onNodeWithTag("debug.haptics.V-KEYBOARD_TAP").performScrollTo().performClick()
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, shadowOf(view).lastHapticFeedbackPerformed())
        composeRule.onNodeWithText("Last played: V-KEYBOARD_TAP").assertExists()
    }
}

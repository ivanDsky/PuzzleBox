package com.ivandsky.puzzlebox.gameui

import android.os.Build
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * The success buzz by API level: the platform's confirm from 30, the view's long press below. Always through the
 * view, never the vibrator, which would need a permission. The runtime here is API 34; the level the helper reads
 * is set for each case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SolveCelebrationHapticsTest {

    private val app = RuntimeEnvironment.getApplication()
    private val view = View(app)
    private val vibrator = shadowOf(app.getSystemService(Vibrator::class.java))
    private val runtimeSdk = Build.VERSION.SDK_INT

    @After
    fun restoreSdk() = setSdk(runtimeSdk)

    private fun setSdk(sdk: Int) = ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)

    private fun buzzOn(sdk: Int) {
        setSdk(sdk)
        view.performSolveHaptic()
    }

    @Test
    fun `API 30 and up buzz the platform's confirm through the view`() {
        listOf(Build.VERSION_CODES.R, runtimeSdk).forEach { sdk ->
            buzzOn(sdk)
            assertEquals("API $sdk", HapticFeedbackConstants.CONFIRM, shadowOf(view).lastHapticFeedbackPerformed())
        }
        assertFalse("the vibrator is left alone", vibrator.isVibrating)
    }

    @Test
    fun `API 29 buzzes the view's long press and leaves the vibrator alone`() {
        buzzOn(Build.VERSION_CODES.Q)

        assertEquals(HapticFeedbackConstants.LONG_PRESS, shadowOf(view).lastHapticFeedbackPerformed())
        assertFalse("the vibrator is left alone", vibrator.isVibrating)
    }

    @Test
    fun `the oldest supported level buzzes the view's long press`() {
        buzzOn(Build.VERSION_CODES.O)

        assertEquals(HapticFeedbackConstants.LONG_PRESS, shadowOf(view).lastHapticFeedbackPerformed())
        assertFalse("the vibrator is left alone", vibrator.isVibrating)
    }
}

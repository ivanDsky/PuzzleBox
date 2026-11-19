package com.ivandsky.puzzlebox.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

/**
 * One UI drops the touch-feedback kinds the app plays (#1686): there each kind plays a predefined Vibrator effect. Where
 * the system drops only the light tick (#1701), only that kind does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class VibratorHapticFeedbackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val calls = ArrayList<HapticFeedbackType>()
    private val platform = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            calls += hapticFeedbackType
        }
    }

    @Test
    fun `each app kind maps to the effect AOSP plays for it, lightest to heaviest`() {
        assertEquals(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK), touchEffect(HapticFeedbackType.TextHandleMove))
        assertEquals(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK), touchEffect(HapticFeedbackType.Confirm))
        assertEquals(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK), touchEffect(HapticFeedbackType.Reject))
        assertEquals(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK), touchEffect(HapticFeedbackType.LongPress))
    }

    @Test
    fun `a mapped kind plays on the vibrator as touch feedback, never on the platform`() {
        VibratorHapticFeedback(vibrator, platform, ::touchEffect).performHapticFeedback(HapticFeedbackType.Reject)

        val attributes = shadowOf(vibrator).vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals("touch usage, so the system touch-feedback switch still silences it", VibrationAttributes.USAGE_TOUCH, attributes.usage)
        assertEquals(emptyList<HapticFeedbackType>(), calls)
    }

    @Test
    fun `a kind with no mapped effect stays on the platform`() {
        VibratorHapticFeedback(vibrator, platform, ::touchEffect).performHapticFeedback(HapticFeedbackType.SegmentTick)

        assertEquals(listOf(HapticFeedbackType.SegmentTick), calls)
        assertNull(shadowOf(vibrator).vibrationAttributesFromLastVibration)
    }

    @Test
    fun `Samsung on Android 13+ gets the full vibrator mapping, even where the system plays the light tick`() {
        ShadowBuild.setManufacturer("samsung")

        deviceHaptics(textHandleTickContext(context, plays = true), platform).performHapticFeedback(HapticFeedbackType.Reject)

        val attributes = shadowOf(vibrator).vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_TOUCH, attributes.usage)
        assertEquals(emptyList<HapticFeedbackType>(), calls)
    }

    @Test
    fun `a maker whose system plays the light tick keeps the platform haptics as they are`() {
        ShadowBuild.setManufacturer("Google")

        assertSame(platform, deviceHaptics(textHandleTickContext(context, plays = true), platform))
    }

    @Test
    @Config(sdk = [32])
    fun `Samsung below Android 13 keeps the platform haptics`() {
        ShadowBuild.setManufacturer("samsung")

        assertSame(platform, deviceHaptics(context, platform))
    }

    @Test
    fun `VIBRATE is declared, or the vibrator throws on a real device`() {
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("android.permission.VIBRATE" in requested)
    }
}

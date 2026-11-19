package com.ivandsky.puzzlebox.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.VibrationAttributes
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
 * One UI drops the touch-feedback kinds the app plays and the Tab S9 Ultra drops its prebaked Vibrator effects too
 * (#1686): there each kind plays plain timings on the Vibrator. Where the system drops only the light tick (#1701), only
 * that kind does.
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
    fun `on One UI every kind plays plain timings as touch feedback, never a prebaked effect the Tab drops`() {
        ShadowBuild.setManufacturer("samsung")
        val haptics = deviceHaptics(context, platform)

        appKinds.forEach { kind ->
            haptics.performHapticFeedback(kind)

            val timings = shadowOf(vibrator).pattern
            assertTrue("$kind played a prebaked effect, which has no timings", timings.isNotEmpty() && timings.all { it > 0 })
            val attributes = shadowOf(vibrator).vibrationAttributesFromLastVibration as VibrationAttributes
            assertEquals("$kind: touch usage, so the system touch-feedback switch still silences it", VibrationAttributes.USAGE_TOUCH, attributes.usage)
        }
        assertEquals(emptyList<HapticFeedbackType>(), calls)
    }

    @Test
    fun `on One UI the tick, confirm and long press rise in length, lightest to heaviest`() {
        ShadowBuild.setManufacturer("samsung")
        val haptics = deviceHaptics(context, platform)

        val lengths = listOf(HapticFeedbackType.TextHandleMove, HapticFeedbackType.Confirm, HapticFeedbackType.LongPress).map {
            haptics.performHapticFeedback(it)
            shadowOf(vibrator).pattern.sum()
        }

        assertEquals(lengths.sorted().distinct(), lengths)
    }

    @Test
    fun `on One UI reject is a double whose pulses are shorter than the 30 ms double click that buzzed hard on the Tab`() {
        ShadowBuild.setManufacturer("samsung")

        deviceHaptics(context, platform).performHapticFeedback(HapticFeedbackType.Reject)

        val timings = shadowOf(vibrator).pattern
        val pulses = timings.filterIndexed { index, _ -> index % 2 == 0 }
        assertEquals("on, off, on", 3, timings.size)
        assertTrue("pulses ${pulses.toList()}", pulses.all { it < 30 })
    }

    @Test
    fun `a kind with no mapped effect stays on the platform`() {
        VibratorHapticFeedback(vibrator, platform, ::timingEffect).performHapticFeedback(HapticFeedbackType.SegmentTick)

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

    private companion object {
        val appKinds = listOf(
            HapticFeedbackType.TextHandleMove,
            HapticFeedbackType.LongPress,
            HapticFeedbackType.Confirm,
            HapticFeedbackType.Reject,
        )
    }
}

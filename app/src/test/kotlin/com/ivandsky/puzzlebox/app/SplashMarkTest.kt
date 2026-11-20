package com.ivandsky.puzzlebox.app

import android.content.Context
import android.graphics.drawable.Animatable
import androidx.core.splashscreen.R as SplashR
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The splash shows the brand-lime mark, not the red-brown launcher foreground: the starting theme
 * names its own animated icon, and the mark is lime in both schemes. The icon is Animatable on
 * purpose: the system pre-draws any other drawable into a 108dp bitmap and upscales it 1.8x to the
 * 192dp splash circle (soft edges), but draws an Animatable one live at the final size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class SplashMarkTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the starting theme takes the splash mark as its animated icon`() {
        val theme = context.resources.newTheme().apply { applyStyle(R.style.Theme_PuzzleBox_Starting, true) }
        val array = theme.obtainStyledAttributes(intArrayOf(SplashR.attr.windowSplashScreenAnimatedIcon))
        val icon = array.getResourceId(0, 0)
        array.recycle()

        assertEquals(R.drawable.ic_splash_mark, icon)
    }

    @Test
    fun `the splash mark is animatable so the system draws it live at full size`() {
        assertTrue(context.getDrawable(R.drawable.ic_splash_mark) is Animatable)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `the splash mark is brand lime in light`() {
        assertEquals(0xFF58CC02.toInt(), context.getColor(R.color.puzzlebox_splash_mark))
    }

    @Test
    @Config(qualifiers = "night")
    fun `the splash mark is brand lime in dark`() {
        assertEquals(0xFF6BDF17.toInt(), context.getColor(R.color.puzzlebox_splash_mark))
    }
}

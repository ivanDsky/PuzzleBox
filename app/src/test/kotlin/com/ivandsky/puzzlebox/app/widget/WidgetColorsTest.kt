package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.color.DayNightColorProvider
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class WidgetColorsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a color role keeps its day value and its night value`() {
        val surface = WidgetColors(context).of(R.color.widget_surface)

        assertTrue(surface is DayNightColorProvider)
        surface as DayNightColorProvider
        assertEquals(Color(context.getColor(R.color.widget_surface)), surface.day)
        assertNotEquals(surface.day, surface.night)
    }
}

package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import android.content.res.Configuration
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Each daily game draws its own glyph and colors on the widget and an unknown game still draws, so a ring is never blank. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "notnight")
class WidgetArtTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val listedGames = listOf("zip", "tango", "queens", "minisudoku", "strands").map(::GameId)
    private val unmapped = GameId("not-a-game")

    private val night: Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        },
    )

    @Test
    fun `each listed game maps to its own glyph and colors, not the fallback`() {
        val glyphs = listedGames.map(WidgetArt::glyph)
        val accents = listedGames.map(WidgetArt::accent)
        val onAccents = listedGames.map(WidgetArt::onAccent)

        assertEquals(listedGames.size, glyphs.toSet().size)
        assertEquals(listedGames.size, accents.toSet().size)
        assertEquals(listedGames.size, onAccents.toSet().size)
        assertTrue(WidgetArt.glyph(unmapped) !in glyphs)
        assertTrue(WidgetArt.accent(unmapped) !in accents)
        assertTrue(WidgetArt.onAccent(unmapped) !in onAccents)
        assertEquals(R.drawable.widget_glyph_zip, WidgetArt.glyph(GameId("zip")))
        assertEquals(R.color.widget_accent_teal, WidgetArt.accent(GameId("minisudoku")))
        assertEquals(R.color.widget_on_accent_pink, WidgetArt.onAccent(GameId("strands")))
    }

    @Test
    fun `an unmapped game gets the plain dot and the neutral colors`() {
        assertEquals(R.drawable.widget_glyph_dot, WidgetArt.glyph(unmapped))
        assertEquals(R.color.widget_accent_neutral, WidgetArt.accent(unmapped))
        assertEquals(R.color.widget_on_accent_neutral, WidgetArt.onAccent(unmapped))
    }

    @Test
    fun `every widget drawable inflates`() {
        val drawables = listedGames.map(WidgetArt::glyph) + WidgetArt.glyph(unmapped) + listOf(
            R.drawable.widget_ring_track,
            R.drawable.widget_ring_half,
            R.drawable.widget_ring_disc,
            R.drawable.widget_ring_dashed,
            R.drawable.widget_flame,
            R.drawable.widget_snowflake,
            R.drawable.widget_play,
        )

        assertEquals(13, drawables.toSet().size)
        drawables.forEach { assertNotNull(context.getDrawable(it)) }
    }

    @Test
    fun `the picker preview inflates as remote views`() {
        val preview = RemoteViews(context.packageName, R.layout.widget_preview).apply(context, FrameLayout(context))

        assertNotNull(preview)
    }

    @Test
    fun `a game accent differs between day and night`() {
        val accents = listedGames.map(WidgetArt::accent) + WidgetArt.accent(unmapped)

        accents.forEach { assertNotEquals(context.getColor(it), night.getColor(it)) }
    }

    @Test
    fun `the widget's own roles have night colors too`() {
        listOf(R.color.widget_track, R.color.widget_muted, R.color.widget_surface, R.color.widget_text, R.color.widget_primary, R.color.widget_on_primary)
            .forEach { assertNotEquals(context.getColor(it), night.getColor(it)) }
    }

    @Test
    fun `the streak line reads in plain words for one day and for many`() {
        val resources = context.resources

        assertEquals("1 day in a row · Best 4", resources.getQuantityString(R.plurals.widget_streak, 1, 1, 4))
        assertEquals("0 days in a row · Best 4", resources.getQuantityString(R.plurals.widget_streak, 0, 0, 4))
        assertEquals("3 days in a row · Best 12", resources.getQuantityString(R.plurals.widget_streak, 3, 3, 12))
        assertEquals("Up next · Queens", context.getString(R.string.widget_up_next_game, "Queens"))
        assertEquals("All done for today", context.getString(R.string.widget_all_done))
    }
}

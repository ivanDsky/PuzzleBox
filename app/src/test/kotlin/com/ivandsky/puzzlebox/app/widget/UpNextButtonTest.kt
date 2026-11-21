package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class UpNextButtonTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun button(state: WidgetState?) = upNextButton(state, context, formatTime = { "9:00 AM" })

    @Test
    fun `a game not started reads Play and asks for up next`() {
        val button = button(WidgetSamples.mixed.copy(upNext = WidgetUpNext.Play(GameId("minisudoku"), "Mini Sudoku")))

        assertEquals("Play Mini Sudoku", button.label)
        assertEquals(ShortcutTarget.UpNext, button.target)
        assertFalse(button.tonal)
    }

    @Test
    fun `a game in progress reads Continue`() {
        assertEquals("Continue Queens", button(WidgetSamples.mixed).label)
    }

    @Test
    fun `everything solved says when new puzzles land, is the quiet pill and opens Today`() {
        val button = button(WidgetSamples.default)

        assertEquals("New puzzles at 9:00 AM", button.label)
        assertEquals(ShortcutTarget.Today, button.target)
        assertTrue(button.tonal)
    }

    @Test
    fun `everything solved with no rollover known still opens Today`() {
        val button = button(WidgetSamples.default.copy(nextRollover = null))

        assertEquals("Open PuzzleBox", button.label)
        assertEquals(ShortcutTarget.Today, button.target)
        assertFalse(button.tonal)
    }

    @Test
    fun `a failed read, an empty Today and an all-offline read say Open PuzzleBox and ask for up next`() {
        listOf(null, WidgetSamples.default.copy(rings = emptyList(), upNext = WidgetUpNext.Unknown), WidgetSamples.offline).forEach {
            val button = button(it)

            assertEquals("Open PuzzleBox", button.label)
            assertEquals(ShortcutTarget.UpNext, button.target)
            assertFalse(button.tonal)
        }
    }

    @Test
    fun `play and open send the shortcut menu's up next intent, with no game id`() {
        val menuIntent = ShortcutIntents.intent(context, ShortcutTarget.UpNext)

        listOf(WidgetSamples.mixed, WidgetSamples.empty).forEach {
            val sent = ShortcutIntents.intent(context, button(it).target)

            assertTrue(sent.filterEquals(menuIntent))
            assertFalse(sent.hasExtra(ShortcutIntents.EXTRA_GAME_ID))
            assertNull(sent.extras)
        }
    }

    @Test
    fun `the rollover is written as the device's short local time`() {
        val zone = TimeZone.getDefault()
        val locale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            Locale.setDefault(Locale.US)

            assertTrue(deviceTime(context, Instant.parse("2026-11-21T09:00:00Z")).contains("9:00"))
        } finally {
            TimeZone.setDefault(zone)
            Locale.setDefault(locale)
        }
    }
}

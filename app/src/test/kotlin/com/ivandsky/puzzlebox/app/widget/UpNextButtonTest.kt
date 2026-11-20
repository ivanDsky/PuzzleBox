package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import com.ivandsky.puzzlebox.model.GameId
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

    private fun button(upNext: WidgetUpNext) = upNextButton(upNext, context)

    @Test
    fun `a game to play names the game and asks for up next`() {
        val button = button(WidgetUpNext.Play(GameId("queens"), "Queens"))

        assertEquals("Up next · Queens", button.label)
        assertEquals(ShortcutTarget.UpNext, button.target)
    }

    @Test
    fun `everything solved says so and opens Today`() {
        val button = button(WidgetUpNext.Done)

        assertEquals("All done for today", button.label)
        assertEquals(ShortcutTarget.Today, button.target)
    }

    @Test
    fun `an unknown reads plain Up next and asks for up next`() {
        val button = button(WidgetUpNext.Unknown)

        assertEquals("Up next", button.label)
        assertEquals(ShortcutTarget.UpNext, button.target)
    }

    @Test
    fun `play and unknown send the shortcut menu's up next intent, with no game id`() {
        val menuIntent = ShortcutIntents.intent(context, ShortcutTarget.UpNext)

        listOf(WidgetUpNext.Play(GameId("queens"), "Queens"), WidgetUpNext.Unknown).forEach {
            val sent = ShortcutIntents.intent(context, button(it).target)

            assertTrue(sent.filterEquals(menuIntent))
            assertFalse(sent.hasExtra(ShortcutIntents.EXTRA_GAME_ID))
            assertNull(sent.extras)
        }
    }
}

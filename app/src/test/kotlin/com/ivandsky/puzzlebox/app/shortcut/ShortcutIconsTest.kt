package com.ivandsky.puzzlebox.app.shortcut

import android.content.Context
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

/** Every daily game gets its own launcher icon and an unknown game still gets one, so a menu entry is never blank. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ShortcutIconsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val listedGames = listOf("zip", "tango", "queens", "minisudoku", "strands").map(::GameId)

    @Test
    fun `each listed game maps to its own icon, not the fallback`() {
        val icons = listedGames.map(ShortcutIcons::forGame)

        assertEquals(listedGames.size, icons.toSet().size)
        assertTrue(R.drawable.ic_shortcut_game !in icons)
        assertEquals(R.drawable.ic_shortcut_zip, ShortcutIcons.forGame(GameId("zip")))
        assertEquals(R.drawable.ic_shortcut_minisudoku, ShortcutIcons.forGame(GameId("minisudoku")))
    }

    @Test
    fun `an unmapped game gets the neutral fallback`() {
        assertEquals(R.drawable.ic_shortcut_game, ShortcutIcons.forGame(GameId("not-a-game")))
    }

    @Test
    fun `up next has its own icon`() {
        assertEquals(R.drawable.ic_shortcut_up_next, ShortcutIcons.upNext)
        assertNotEquals(R.drawable.ic_shortcut_game, ShortcutIcons.upNext)
    }

    @Test
    fun `all seven icons inflate`() {
        val all = listedGames.map(ShortcutIcons::forGame) + ShortcutIcons.upNext + ShortcutIcons.forGame(GameId("not-a-game"))

        assertEquals(7, all.toSet().size)
        all.forEach { assertNotNull(context.getDrawable(it)) }
    }

    @Test
    fun `labels read in plain words`() {
        assertEquals("Up next", context.getString(R.string.shortcut_up_next_short))
        assertEquals("Play Up next", context.getString(R.string.shortcut_up_next_long))
        assertEquals("Play Zip", context.getString(R.string.shortcut_game_long, "Zip"))
    }
}

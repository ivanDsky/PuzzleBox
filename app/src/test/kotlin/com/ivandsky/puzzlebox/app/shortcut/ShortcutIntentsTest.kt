package com.ivandsky.puzzlebox.app.shortcut

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.MainActivity
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ShortcutIntentsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `up next round-trips`() {
        assertEquals(ShortcutTarget.UpNext, ShortcutIntents.targetOf(ShortcutIntents.intent(context, ShortcutTarget.UpNext)))
    }

    @Test
    fun `a game round-trips`() {
        val target = ShortcutTarget.Game(GameId("queens"))

        assertEquals(target, ShortcutIntents.targetOf(ShortcutIntents.intent(context, target)))
    }

    @Test
    fun `the intent is explicit to the main activity`() {
        val intent = ShortcutIntents.intent(context, ShortcutTarget.UpNext)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(ShortcutIntents.ACTION, intent.action)
    }

    @Test
    fun `a foreign intent and no intent give null`() {
        assertNull(ShortcutIntents.targetOf(Intent(Intent.ACTION_MAIN)))
        assertNull(ShortcutIntents.targetOf(null))
    }

    @Test
    fun `a game with a blank id gives null`() {
        val blank = Intent(ShortcutIntents.ACTION).putExtra(ShortcutIntents.EXTRA_GAME_ID, "  ")

        assertNull(ShortcutIntents.targetOf(blank))
    }
}

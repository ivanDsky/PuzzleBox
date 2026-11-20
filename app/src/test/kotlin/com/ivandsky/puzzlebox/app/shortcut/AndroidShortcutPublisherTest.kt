package com.ivandsky.puzzlebox.app.shortcut

import android.content.Context
import android.content.pm.ShortcutManager
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class AndroidShortcutPublisherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val publisher = AndroidShortcutPublisher(context)
    private val entries = BuildShortcutEntries()(listOf(ShortcutGame(GameId("zip"), "Zip"), ShortcutGame(GameId("minisudoku"), "Mini Sudoku")))

    private fun published() = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }

    @Test
    fun `entries become shortcuts with stable ids and ranks`() {
        publisher.publish(entries)

        assertEquals(listOf("up_next", "game_zip", "game_minisudoku"), published().map { it.id })
        assertEquals(listOf(0, 1, 2), published().map { it.rank })
    }

    @Test
    fun `labels read Up next and the game name, long labels Play`() {
        publisher.publish(entries)

        assertEquals(listOf("Up next", "Zip", "Mini Sudoku"), published().map { it.shortLabel.toString() })
        assertEquals(listOf("Play Up next", "Play Zip", "Play Mini Sudoku"), published().map { it.longLabel.toString() })
    }

    @Test
    fun `each shortcut opens its target through the shortcut intent`() {
        publisher.publish(entries)

        val intents = published().map { it.intent }
        assertTrue(intents.all { it.action == ShortcutIntents.ACTION })
        assertEquals(listOf(ShortcutTarget.UpNext, ShortcutTarget.Game(GameId("zip")), ShortcutTarget.Game(GameId("minisudoku"))), intents.map(ShortcutIntents::targetOf))
    }

    @Test
    fun `publish replaces the earlier set and clear removes it`() {
        publisher.publish(entries)
        publisher.publish(entries.take(1))
        assertEquals(listOf("up_next"), published().map { it.id })

        publisher.clear()
        assertTrue(published().isEmpty())
    }

    @Test
    fun `a launcher with fewer slots than entries keeps the top ranks and drops the last`() {
        shadowOf(context.getSystemService(ShortcutManager::class.java)).setMaxShortcutCountPerActivity(2)

        publisher.publish(entries)

        assertEquals(listOf("up_next", "game_zip"), published().map { it.id })
    }
}

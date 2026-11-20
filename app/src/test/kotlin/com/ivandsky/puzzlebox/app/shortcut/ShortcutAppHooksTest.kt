package com.ivandsky.puzzlebox.app.shortcut

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The start hook's two branches. Robolectric only for the log line the failure case writes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ShortcutAppHooksTest {

    private val games = listOf("zip" to "Zip", "tango" to "Tango", "queens" to "Queens", "minisudoku" to "Mini Sudoku", "strands" to "Strands")
        .map { (id, name) -> ShortcutGame(GameId(id), name) }
    private val uncaught = mutableListOf<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> uncaught += e })

    private fun hooks(enabled: Boolean, publisher: ShortcutPublisher) =
        ShortcutAppHooks(enabled, games = { games }, publisher = publisher, scope = scope)

    @Test
    fun `flag on publishes up next first and the five games after it in order`() {
        val publisher = FakeShortcutPublisher()

        hooks(enabled = true, publisher).start()

        val entries = publisher.published!!
        assertEquals(listOf(0, 1, 2, 3, 4, 5), entries.map { it.rank })
        assertEquals(ShortcutTarget.UpNext, entries.first().target)
        assertEquals(games.map { it.displayName }, entries.drop(1).map { it.gameName })
        assertEquals(0, publisher.clears)
    }

    @Test
    fun `flag off clears earlier entries and publishes nothing`() {
        val publisher = FakeShortcutPublisher()

        hooks(enabled = false, publisher).start()

        assertNull(publisher.published)
        assertEquals(1, publisher.clears)
    }

    @Test
    fun `a publisher failure does not escape the start`() {
        hooks(enabled = true, FakeShortcutPublisher(failure = IllegalStateException("user locked"))).start()
        hooks(enabled = false, FakeShortcutPublisher(failure = IllegalStateException("user locked"))).start()

        assertEquals(emptyList<Throwable>(), uncaught)
    }
}

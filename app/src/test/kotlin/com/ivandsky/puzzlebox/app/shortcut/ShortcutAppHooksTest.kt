package com.ivandsky.puzzlebox.app.shortcut

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The start hook publishes the menu and survives a failing publisher. Robolectric only for the log line the failure case writes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ShortcutAppHooksTest {

    private val games = listOf("zip" to "Zip", "tango" to "Tango", "queens" to "Queens", "minisudoku" to "Mini Sudoku", "strands" to "Strands")
        .map { (id, name) -> ShortcutGame(GameId(id), name) }
    private val uncaught = mutableListOf<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> uncaught += e })

    private fun hooks(publisher: ShortcutPublisher) =
        ShortcutAppHooks(games = { games }, publisher = publisher, scope = scope)

    @Test
    fun `the start publishes up next first and the five games after it in order`() {
        val publisher = FakeShortcutPublisher()

        hooks(publisher).start()

        val entries = publisher.published!!
        assertEquals(listOf(0, 1, 2, 3, 4, 5), entries.map { it.rank })
        assertEquals(ShortcutTarget.UpNext, entries.first().target)
        assertEquals(games.map { it.displayName }, entries.drop(1).map { it.gameName })
    }

    @Test
    fun `a publisher failure does not escape the start`() {
        hooks(FakeShortcutPublisher(failure = IllegalStateException("user locked"))).start()

        assertEquals(emptyList<Throwable>(), uncaught)
    }
}

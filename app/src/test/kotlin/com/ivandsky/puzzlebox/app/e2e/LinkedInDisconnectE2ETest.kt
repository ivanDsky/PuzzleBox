package com.ivandsky.puzzlebox.app.e2e

import android.os.Looper
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.GHOSTS
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.PROFILE_ID
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.ZIP
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.zipRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.linkedin.LinkedInSync
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Disconnecting removes everything that came with the account (its cookies, the connections' rows,
 * the read log and the link itself) and keeps what became the player's own: the imported result.
 * After it, looking at a day asks LinkedIn nothing.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class LinkedInDisconnectE2ETest {

    private val world = LinkedInDataWorld()
    private val today = E2E_TODAY
    private val yesterday = E2E_TODAY.minusDays(1)

    @After
    fun closeWorld() {
        world.close()
    }

    @Test
    fun disconnectWipesTheAccountAndKeepsTheImports() {
        world.scriptDay(today, LinkedInOwnResult.Solved(timeSeconds = 70, hintFree = true), GHOSTS)
        // Answerable, so a read of this day after the disconnect would show as a call and an import.
        world.scriptDay(yesterday, LinkedInOwnResult.Solved(timeSeconds = 88, hintFree = true), GHOSTS)
        shellRobot(graph = world.graph) {
            runBlocking { world.connect() }
            openZipPage()
            awaitSynced(today)
            assertEquals("the connections' rows were stored", GHOSTS.size, storedGhosts())
            assertTrue("the link is on disk", linkFileNames(PROFILE_ID))

            runBlocking { link().disconnect() }

            assertTrue("the session cookies are cleared", world.cookies.cleared)
            assertEquals("no connection's row is left", 0, storedGhosts())
            assertEquals("the read log is empty", 0, runBlocking { linkedInDb().fetchLogDao().observe(ZIP, today.toString(), PROFILE_ID).first() }.size)
            assertEquals(LinkState.NotConnected, runBlocking { link().state.first() })
            assertFalse("the link file no longer names the profile", linkFileNames(PROFILE_ID))
            val kept = runBlocking { completions().observeRecords().first() }
            assertEquals("the imported result stays", listOf(zipRef(today) to CompletionSource.LinkedIn), kept.map { it.puzzleRef to it.source })

            // The same day again, then another: each look waits out the day view's hold-back.
            val calls = world.source.calls
            reopenZipPage()
            settle()
            pickDay(yesterday)
            settle()
            // Awaiting the day's sync to its end also waits out one the day view may still be running.
            assertEquals(LinkedInSync.NotLinked, runBlocking { graph.koin.get<LinkedInDays>().sync(ZipDefinition.id, yesterday) })
            assertEquals("no call after the disconnect", calls, world.source.calls)
            assertEquals("and so nothing new imported", kept, runBlocking { completions().observeRecords().first() })
        }
    }

    private fun ShellRobot.storedGhosts(): Int =
        runBlocking { linkedInDb().ghostDao().observe(ZIP, today.toString()).first() }.size

    /** Moves the main looper well past the day view's hold-back, then lets the screen settle. */
    private fun ShellRobot.settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        rule.waitForIdle()
    }

    /** Whether the link's preferences file exists and holds [text]. */
    private fun linkFileNames(text: String): Boolean {
        val file = RuntimeEnvironment.getApplication().filesDir.resolve("linkedin_link.preferences_pb")
        return file.exists() && text in file.readBytes().toString(Charsets.ISO_8859_1)
    }
}

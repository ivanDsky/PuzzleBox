package com.ivandsky.puzzlebox.app.e2e

import android.os.Looper
import com.ivandsky.puzzlebox.app.DrainTestApplication
import java.io.File
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.GHOSTS
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.PROFILE_ID
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.ZIP
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.zipRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.linkedin.LinkedInSync
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.SavedBoardEntity
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Disconnecting wipes what only the account needed (its cookies, the read log, the photos and the link
 * itself) and keeps what became the player's own: the imported result, the connections' rows and the saved
 * board of each day read. After it, looking at a day asks LinkedIn nothing and changes none of them.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class LinkedInDisconnectE2ETest {

    private val world = LinkedInDataWorld()
    private val today = E2E_TODAY
    private val yesterday = E2E_TODAY.minusDays(1)

    @After
    fun closeWorld() {
        world.close()
    }

    @Test
    fun disconnectKeepsTheBoardsAndWipesTheAccount() {
        world.scriptDay(today, LinkedInOwnResult.Solved(timeSeconds = 70, hintFree = true), GHOSTS)
        // Answerable, so a read of this day after the disconnect would show as a call and an import.
        world.scriptDay(yesterday, LinkedInOwnResult.Solved(timeSeconds = 88, hintFree = true), GHOSTS)
        shellRobot(graph = world.graph) {
            runBlocking { world.connect() }
            openZipPage()
            awaitSynced(today)
            assertEquals("the connections' rows were stored", GHOSTS.size, storedGhostRows().size)
            assertTrue("the link is on disk", linkFileNames(PROFILE_ID))
            // Photos come after the log entry, so the day can be read before the last of them is on disk.
            offMain {
                graph.koin.get<LinkedInDays>().observe(ZipDefinition.id, today)
                    .first { day -> day.ghosts.size == GHOSTS.size && day.ghosts.all { it.photoPath != null } }
            }
            assertEquals("one photo file per connection", GHOSTS.size, avatarFiles().size)
            assertTrue("no photo file is named after a member", avatarFiles().none { "GHOST" in it.name })

            val keptGhosts = storedGhostRows()
            val keptBoard = checkNotNull(savedBoard(today)) { "the board read was saved" }
            assertTrue("with the time it was read", keptBoard.readAtMs > 0L)
            // Today is not over, so the board is partial; no account was signed in, so it has no owner.
            assertEquals(SavedBoardEntity(ZIP, today.toString(), PROFILE_ID, null, keptBoard.readAtMs, false, null), keptBoard)

            offMain { link().disconnect() }

            assertTrue("the session cookies are cleared", world.cookies.cleared)
            assertEquals("every connection's row is kept", keptGhosts, storedGhostRows())
            assertEquals("the saved board is kept", keptBoard, savedBoard(today))
            assertEquals("the read log is empty", 0, runBlocking { linkedInDb().fetchLogDao().observe(ZIP, today.toString(), PROFILE_ID).first() }.size)
            assertEquals(LinkState.NotConnected, runBlocking { link().state.first() })
            assertFalse("the link file no longer names the profile", linkFileNames(PROFILE_ID))
            assertTrue("no connection's photo is left", avatarFiles().isEmpty())
            val kept = runBlocking { completions().observeRecords().first() }
            assertEquals("the imported result stays", listOf(zipRef(today) to CompletionSource.LinkedIn), kept.map { it.puzzleRef to it.source })

            // The same day again, then another: each look waits out the day view's hold-back.
            val photosAsked = world.photos.calls
            val calls = world.source.calls
            reopenZipPage()
            settle()
            pickDay(yesterday)
            settle()
            // Awaiting the day's sync to its end also waits out one the day view may still be running.
            assertEquals(LinkedInSync.NotLinked, runBlocking { graph.koin.get<LinkedInDays>().sync(ZipDefinition.id, yesterday) })
            assertEquals("no call after the disconnect", calls, world.source.calls)
            assertEquals("and so nothing new imported", kept, runBlocking { completions().observeRecords().first() })
            assertEquals("no photo asked for after the disconnect", photosAsked, world.photos.calls)
            assertTrue("and none stored", avatarFiles().isEmpty())
            assertEquals("no ghost written or removed by the later looks", keptGhosts, storedGhostRows())
            assertEquals("the saved board is as the read left it", keptBoard, savedBoard(today))
            assertNull("and no board saved for the day looked at after", savedBoard(yesterday))
        }
    }

    /**
     * Runs [block] on another thread while this one keeps turning the main looper, so work that needs the
     * main thread to finish (the day view's collectors, a photo's last step) is never waited on from it.
     */
    private fun <T> ShellRobot.offMain(block: suspend () -> T): T {
        val done = CompletableFuture.supplyAsync { runBlocking { block() } }
        awaitLooper { done.isDone }
        return done.get()
    }

    private fun ShellRobot.storedGhostRows(): Set<GhostEntity> =
        runBlocking { linkedInDb().ghostDao().board(ZIP, today.toString()) }.toSet()

    private fun ShellRobot.savedBoard(day: LocalDate): SavedBoardEntity? =
        runBlocking { linkedInDb().savedBoardDao().get(ZIP, day.toString()) }

    /** Moves the main looper well past the day view's hold-back, then lets the screen settle. */
    private fun ShellRobot.settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        rule.waitForIdle()
    }

    /** The cached photos' directory; its files are all the connections' faces the phone holds. */
    private fun avatarFiles(): List<File> =
        File(RuntimeEnvironment.getApplication().noBackupFilesDir, "linkedin_avatars").listFiles().orEmpty().toList()

    /** Whether the link's preferences file exists and holds [text]. */
    private fun linkFileNames(text: String): Boolean {
        val file = RuntimeEnvironment.getApplication().filesDir.resolve("linkedin_link.preferences_pb")
        return file.exists() && text in file.readBytes().toString(Charsets.ISO_8859_1)
    }
}

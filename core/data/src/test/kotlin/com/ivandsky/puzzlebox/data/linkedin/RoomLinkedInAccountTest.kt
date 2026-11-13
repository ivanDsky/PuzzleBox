package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import app.cash.turbine.test
import com.ivandsky.puzzlebox.database.linkedin.FetchLogEntity
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What the phone keeps of the linked account: the owner's name and photo, the connections seen, the last update. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLinkedInAccountTest {

    private val link = FakeLinkedInLink(LinkState.Connected("me"))
    private val dir = File(createTempDirectory("avatars").toFile(), "linkedin_avatars")
    private val avatars = testAvatars(dir)
    private lateinit var db: LinkedInDatabase
    private lateinit var account: LinkedInAccount

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        account = RoomLinkedInAccount(link, db, avatars, FakeOwnerScope())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun ghost(ref: String, name: String = "Name $ref", game: String = "queens", day: String = "2026-11-01", at: Long = 1_000L) =
        GhostEntity(game, day, ref, name, 60_000L, hintFree = true, flawless = false, fetchedAtMs = at)

    private fun logged(at: Long, day: String = "2026-11-01", profile: String = "me") =
        FetchLogEntity("queens", day, "Own", profile, at, "Solved")

    @Test
    fun `an unlinked phone reports nothing even when rows are left over`() = runTest {
        link.disconnect()
        db.ghostDao().replace("queens", "2026-11-01", listOf(ghost("me"), ghost("a")))

        assertEquals(LinkedInAccountData(owner = null, connectionsSeen = 0, lastFetchedAtMs = null), account.observe().first())
    }

    @Test
    fun `a linked phone that has read nothing reports zeros and no name`() = runTest {
        assertEquals(LinkedInAccountData(owner = null, connectionsSeen = 0, lastFetchedAtMs = null), account.observe().first())
    }

    @Test
    fun `the name is the newest row of the owner and connections are counted without the owner`() = runTest {
        db.ghostDao().replace("queens", "2026-11-01", listOf(ghost("me", "Old Name", at = 1_000L), ghost("a"), ghost("b")))
        db.ghostDao().replace("zip", "2026-11-02", listOf(ghost("me", "New Name", game = "zip", day = "2026-11-02", at = 5_000L), ghost("a", game = "zip", day = "2026-11-02"), ghost("c", game = "zip", day = "2026-11-02")))

        val data = account.observe().first()

        assertEquals(LinkedInOwner("New Name", null), data.owner)
        assertEquals(3, data.connectionsSeen)
    }

    @Test
    fun `the last update is the newest read of this profile`() = runTest {
        db.fetchLogDao().put(logged(2_000L))
        db.fetchLogDao().put(logged(8_000L, day = "2026-11-02"))
        db.fetchLogDao().put(logged(99_000L, profile = "someone else"))

        assertEquals(8_000L, account.observe().first().lastFetchedAtMs)
    }

    @Test
    fun `a paused link keeps its numbers and a disconnect empties them`() = runTest {
        db.ghostDao().replace("queens", "2026-11-01", listOf(ghost("me"), ghost("a")))
        db.fetchLogDao().put(logged(3_000L))
        link.pause(Kind.RateLimited)
        val paused = account.observe().first()
        assertEquals(LinkedInOwner("Name me", null), paused.owner)
        assertEquals(3_000L, paused.lastFetchedAtMs)

        account.observe().test {
            assertEquals(1, awaitItem().connectionsSeen)
            // A pause keeps the link's profile, so the numbers neither change nor emit again.
            link.pause(Kind.RateLimited)
            expectNoEvents()
            link.disconnect()
            assertNull(awaitItem().owner)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

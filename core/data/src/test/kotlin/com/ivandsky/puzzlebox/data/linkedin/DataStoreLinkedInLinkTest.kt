package com.ivandsky.puzzlebox.data.linkedin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ivandsky.puzzlebox.database.linkedin.FetchLogEntity
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.datastore.LinkedInLinkDataSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInSessionCookies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * The link over a real Preferences DataStore file and real in-memory Room, so the latch
 * and the wipe are checked against what is actually stored. A second link over the same
 * file stands in for a new process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreLinkedInLinkTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: LinkedInDatabase
    private lateinit var source: LinkedInLinkDataSource
    private var cookies: LinkedInSessionCookies = FakeLinkedInSessionCookies(session = null)
    private var process: CoroutineScope? = null

    private val ghost = GhostEntity(
        game = "queens",
        day = "2026-11-01",
        memberRef = "friend",
        name = "Friend",
        timeMs = 60_000L,
        hintFree = true,
        flawless = false,
        fetchedAtMs = 1_000L,
    )
    private val fetch = FetchLogEntity(
        game = "queens",
        day = "2026-11-01",
        item = "Board",
        profileId = "profile-1",
        fetchedAtMs = 1_000L,
        outcome = "Board",
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        process?.cancel()
        db.close()
    }

    /**
     * DataStore allows one live instance per file — one per process. Starting again cancels
     * the previous scope first, which ends that "process" so this one can open the file.
     */
    private fun TestScope.start(): DataStoreLinkedInLink {
        process?.cancel()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        process = scope
        val file = File(folder.root, "linkedin_link.preferences_pb")
        source = LinkedInLinkDataSource(PreferenceDataStoreFactory.create(scope = scope) { file })
        return DataStoreLinkedInLink(source, db, cookies)
    }

    private suspend fun ghosts(): List<GhostEntity> = db.ghostDao().observe(ghost.game, ghost.day).first()

    private suspend fun fetchLog(): FetchLogEntity? =
        db.fetchLogDao().get(fetch.game, fetch.day, fetch.item, fetch.profileId)

    @Test
    fun `a fresh install is NotConnected`() = runTest {
        assertEquals(LinkState.NotConnected, start().state.first())
    }

    @Test
    fun `connect makes the link Connected with the profile id`() = runTest {
        val link = start()

        link.connect("profile-1", 1_000L)

        assertEquals(LinkState.Connected("profile-1"), link.state.first())
    }

    @Test
    fun `the latch survives a restart`() = runTest {
        for (kind in Kind.entries) {
            val link = start()
            link.connect("profile-1", 1_000L)

            link.pause(kind)

            assertEquals(LinkState.Paused("profile-1", kind), link.state.first())
            assertEquals(LinkState.Paused("profile-1", kind), start().state.first())
        }
    }

    @Test
    fun `resume clears only the pause`() = runTest {
        val link = start()
        link.connect("profile-1", 1_000L)
        link.pause(Kind.RateLimited)

        link.resume()

        assertEquals(LinkState.Connected("profile-1"), link.state.first())
        assertEquals(LinkState.Connected("profile-1"), start().state.first())
    }

    @Test
    fun `reconnect clears the latch`() = runTest {
        val link = start()
        link.connect("profile-1", 1_000L)
        link.pause(Kind.SessionExpired)

        link.connect("profile-2", 2_000L)

        assertEquals(LinkState.Connected("profile-2"), link.state.first())
        assertEquals(LinkState.Connected("profile-2"), start().state.first())
    }

    @Test
    fun `an unknown stored reason reads as Unexpected`() = runTest {
        val link = start()
        link.connect("profile-1", 1_000L)

        source.pause("FromTheFuture")

        assertEquals(LinkState.Paused("profile-1", Kind.Unexpected), link.state.first())
    }

    @Test
    fun `pausing while not connected stays NotConnected`() = runTest {
        val link = start()

        link.pause(Kind.Challenge)

        assertEquals(LinkState.NotConnected, link.state.first())
        assertEquals(LinkState.NotConnected, start().state.first())
    }

    @Test
    fun `connecting after a stray pause is Connected, not Paused`() = runTest {
        val link = start()
        link.pause(Kind.Challenge)

        link.connect("profile-1", 1_000L)

        assertEquals(LinkState.Connected("profile-1"), link.state.first())
    }

    @Test
    fun `disconnect wipes cookies, ghosts, fetch log and link state`() = runTest {
        val session = FakeLinkedInSessionCookies(LinkedInAccountSession("li_at=a; JSESSIONID=b", "b", "agent"))
        cookies = session
        val link = start()
        link.connect("profile-1", 1_000L)
        db.ghostDao().insertAll(listOf(ghost))
        db.fetchLogDao().put(fetch)
        link.pause(Kind.Challenge)
        assertEquals(listOf(ghost), ghosts())
        assertEquals(fetch, fetchLog())

        link.disconnect()

        assertTrue(session.cleared)
        assertEquals(emptyList<GhostEntity>(), ghosts())
        assertNull(fetchLog())
        assertEquals(LinkState.NotConnected, link.state.first())
        assertEquals(LinkState.NotConnected, start().state.first())
    }

    @Test
    fun `a disconnect that fails at the cookies leaves the link and its data in place`() = runTest {
        cookies = object : LinkedInSessionCookies {
            override suspend fun current(): LinkedInAccountSession? = null
            override suspend fun clear(): Unit = throw IOException("cookie store unavailable")
        }
        val link = start()
        link.connect("profile-1", 1_000L)
        db.ghostDao().insertAll(listOf(ghost))
        db.fetchLogDao().put(fetch)
        link.pause(Kind.Challenge)

        try {
            link.disconnect()
            fail("disconnect should surface the failure")
        } catch (expected: IOException) {
            // The player can press Disconnect again: nothing was half-wiped.
        }

        assertEquals(LinkState.Paused("profile-1", Kind.Challenge), link.state.first())
        assertEquals(listOf(ghost), ghosts())
        assertEquals(fetch, fetchLog())
    }
}

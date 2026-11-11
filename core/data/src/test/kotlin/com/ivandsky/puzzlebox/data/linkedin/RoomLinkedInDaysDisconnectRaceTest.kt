package com.ivandsky.puzzlebox.data.linkedin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.datastore.LinkedInLinkDataSource
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A read that is already on the wire when Disconnect begins must not leave anything behind: the real link
 * and the real day sync share their lock, over real Room and DataStore, and the test holds each side at the
 * point where the other would otherwise slip in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLinkedInDaysDisconnectRaceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val zip = GameId("zip")
    private val today = LocalDate.of(2026, 11, 5)
    private val day = today.minusDays(3)
    private val source = FakeLinkedInAccountSource()
    private val storeLock = LinkedInStoreLock()
    private val avatars = testAvatars(createTempDirectory("avatars").toFile())
    private val daily = FakeDailyRepository().apply {
        setPuzzleNumbers(zip) { number(it) }
        setLinkedInGameType(zip, 6)
    }
    private lateinit var db: LinkedInDatabase
    private var process: CoroutineScope? = null

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        source.respondOwn("me", 6, number(day), LinkedInRead.Ok(LinkedInOwnResult.Solved(timeSeconds = 70, hintFree = true)))
        source.respondBoard("me", 6, number(today), 3, LinkedInRead.Ok(LinkedInBoard(listOf(BOARD_ROW))))
    }

    @After
    fun tearDown() {
        process?.cancel()
        db.close()
    }

    private fun number(day: LocalDate): Int = 700 + ChronoUnit.DAYS.between(EPOCH, day).toInt()

    private fun TestScope.link(cookies: LinkedInSessionCookies): DataStoreLinkedInLink {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        process = scope
        val file = File(folder.root, "linkedin_link.preferences_pb")
        val data = LinkedInLinkDataSource(PreferenceDataStoreFactory.create(scope = scope) { file })
        return DataStoreLinkedInLink(data, db, cookies, avatars, storeLock)
    }

    private fun days(link: LinkedInLink, completions: CompletionRepository) = RoomLinkedInDays(
        link, source, completions, daily, FakeGameCalendar(today), TestGames.registry(listOf(zip)), db,
        LinkedInCallBudget({ NOW }, limit = 100), avatars, storeLock,
    ) { NOW }

    private suspend fun assertNothingLeftOfTheAccount(link: LinkedInLink) {
        assertNull(db.fetchLogDao().get(zip.value, day.toString(), LinkedInItem.Own.name, "me"))
        assertNull(db.fetchLogDao().get(zip.value, day.toString(), LinkedInItem.Board.name, "me"))
        assertEquals(emptyList<Any>(), db.ghostDao().observe(zip.value, day.toString()).first())
        assertEquals(LinkState.NotConnected, link.state.first())
    }

    @Test
    fun `a read that lands while the wipe is running stores no ghost, no log row and no import`() = runTest {
        val wiping = CompletableDeferred<Unit>()
        val finishWipe = CompletableDeferred<Unit>()
        val cookies = object : LinkedInSessionCookies {
            override suspend fun current(): LinkedInAccountSession? = null
            override suspend fun clear() {
                wiping.complete(Unit)
                finishWipe.await()
            }
        }
        val link = link(cookies)
        val completions = FakeCompletionRepository()
        link.connect("me", 1_000L)
        val disconnect = launch { link.disconnect() }
        wiping.await()

        // The link still reads as connected, so the read passes every check but the lock.
        val sync = async { days(link, completions).sync(zip, day) }
        advanceUntilIdle()
        finishWipe.complete(Unit)
        advanceUntilIdle()
        disconnect.join()

        assertEquals(LinkedInSync.NotLinked, sync.await())
        assertEquals(emptyList<CompletionRecord>(), completions.observeImports().first())
        assertNothingLeftOfTheAccount(link)
    }

    @Test
    fun `a store that began before Disconnect is wiped with the rest`() = runTest {
        val link = link(object : LinkedInSessionCookies {
            override suspend fun current(): LinkedInAccountSession? = null
            override suspend fun clear() = Unit
        })
        val storing = CompletableDeferred<Unit>()
        val finishStore = CompletableDeferred<Unit>()
        val completions = HeldImports(FakeCompletionRepository(), storing, finishStore)
        link.connect("me", 1_000L)
        val sync = async { days(link, completions).sync(zip, day) }
        storing.await()

        val disconnect = launch { link.disconnect() }
        advanceUntilIdle()
        finishStore.complete(Unit)
        advanceUntilIdle()
        disconnect.join()
        sync.await()

        assertNothingLeftOfTheAccount(link)
    }

    /** Parks the first import between the sync's last link check and its stores, until [release] completes. */
    private class HeldImports(
        private val delegate: CompletionRepository,
        private val entered: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>,
    ) : CompletionRepository by delegate {
        override suspend fun onCompleted(record: CompletionRecord) {
            entered.complete(Unit)
            release.await()
            delegate.onCompleted(record)
        }
    }

    private companion object {
        val EPOCH: LocalDate = LocalDate.of(2026, 1, 1)
        const val NOW = 1_800_000_000_000L
        val BOARD_ROW = LinkedInBoard.Row("friend", "Friend", 60, hintFree = true, flawless = false, pictureUrl = null)
    }
}

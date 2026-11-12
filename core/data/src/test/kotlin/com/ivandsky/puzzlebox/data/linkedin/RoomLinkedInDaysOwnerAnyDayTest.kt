package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPhotoSource
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The player's LinkedIn name and photo belong to the connected account, so a day whose own board never named
 * them still shows the newest row stored for the same profile, and never another profile's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLinkedInDaysOwnerAnyDayTest {

    private val zip = GameId("zip")
    private val queens = GameId("queens")
    private val registry = TestGames.registry(listOf(zip, queens))
    private val today = LocalDate.of(2026, 11, 5)
    private val link = FakeLinkedInLink(LinkState.Connected("me"))
    private val photos = FakeLinkedInPhotoSource()
    private val avatars = testAvatars(createTempDirectory("avatars").toFile(), photos)
    private lateinit var db: LinkedInDatabase
    private lateinit var days: RoomLinkedInDays

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        days = RoomLinkedInDays(
            link,
            FakeLinkedInAccountSource(),
            FakeCompletionRepository(),
            FakeDailyRepository(),
            FakeGameCalendar(today),
            registry,
            db,
            LinkedInCallBudget({ NOW }, limit = 100),
            avatars,
            LinkedInStoreLock(),
            FakeOwnerScope(),
        ) { NOW }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(memberRef: String, name: String, game: GameId, day: LocalDate, fetchedAtMs: Long) =
        GhostEntity(game.value, day.toString(), memberRef, name, 60_000L, hintFree = true, flawless = false, fetchedAtMs = fetchedAtMs)

    private suspend fun store(game: GameId, day: LocalDate, vararg rows: GhostEntity) =
        db.ghostDao().replace(game.value, day.toString(), rows.toList())

    private suspend fun ownerOf(game: GameId, day: LocalDate): LinkedInOwner? = days.observe(game, day).first().owner

    @Test
    fun `a day with no owner row takes the newest owner row of the same profile from another game and day`() = runTest {
        store(queens, today.minusDays(5), row("me", "Old Name", queens, today.minusDays(5), fetchedAtMs = 1_000L))
        store(zip, today.minusDays(1), row("me", "New Name", zip, today.minusDays(1), fetchedAtMs = 2_000L))
        store(zip, today.minusDays(9), row("friend", "Friend", zip, today.minusDays(9), fetchedAtMs = 3_000L))

        assertEquals(LinkedInOwner("New Name", photoPath = null), ownerOf(zip, today.minusDays(9)))
        assertEquals(LinkedInOwner("New Name", photoPath = null), ownerOf(queens, today.minusDays(20)))
    }

    @Test
    fun `the owner's own row of the day still wins over a newer one from another day`() = runTest {
        store(zip, today.minusDays(3), row("me", "Day Name", zip, today.minusDays(3), fetchedAtMs = 1_000L))
        store(zip, today.minusDays(1), row("me", "New Name", zip, today.minusDays(1), fetchedAtMs = 2_000L))

        assertEquals("Day Name", ownerOf(zip, today.minusDays(3))?.name)
    }

    @Test
    fun `another profile's rows are never the owner`() = runTest {
        store(zip, today.minusDays(1), row("friend", "Friend", zip, today.minusDays(1), fetchedAtMs = 2_000L))
        store(queens, today.minusDays(2), row("earlier-profile", "Earlier", queens, today.minusDays(2), fetchedAtMs = 3_000L))

        assertNull(ownerOf(zip, today.minusDays(9)))
        assertEquals(listOf("friend"), days.observe(zip, today.minusDays(1)).first().ghosts.map { it.memberRef })
    }

    @Test
    fun `the owner's photo is the one cached for the account, whichever day it shows`() = runTest {
        photos.respond("https://media.licdn.com/p/me", byteArrayOf(1, 2, 3))
        store(zip, today.minusDays(1), row("me", "Me", zip, today.minusDays(1), fetchedAtMs = 2_000L))
        avatars.download(listOf(LinkedInBoard.Row("me", "Me", 60, hintFree = true, flawless = false, pictureUrl = "https://media.licdn.com/p/me")))

        val owner = checkNotNull(ownerOf(queens, today.minusDays(7)))
        assertEquals("Me", owner.name)
        assertEquals(avatars.pathOf("me", avatars.present.first()), owner.photoPath)
        assertNotNull(owner.photoPath)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}

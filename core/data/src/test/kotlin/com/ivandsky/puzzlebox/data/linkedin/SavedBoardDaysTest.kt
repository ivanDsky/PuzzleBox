package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import app.cash.turbine.test
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import java.time.LocalDate
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

/**
 * A day's saved board as the phone shows it without any live link, over real in-memory Room: whichever days were
 * read stay readable, the player's own row is told apart by the board's profile, and another account's board is
 * not there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedBoardDaysTest {

    private val zip = GameId("zip")
    private val today = LocalDate.of(2026, 11, 10)
    private val owners = FakeOwnerScope()
    private val avatars = testAvatars(createTempDirectory("avatars").toFile())
    private lateinit var db: LinkedInDatabase
    private lateinit var writer: LinkedInGhostWriter
    private lateinit var reader: SavedBoardDays

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        writer = LinkedInGhostWriter(db, owners)
        reader = SavedBoardDays(db, avatars, owners)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(ref: String, name: String = "Name $ref", timeSeconds: Int? = 60) =
        LinkedInBoard.Row(ref, name, timeSeconds, hintFree = true, flawless = false)

    private fun ghost(ref: String, timeMs: Long? = 60_000) = LinkedInGhost(ref, "Name $ref", timeMs, hintFree = true, flawless = false)

    /** [profile]'s read of [day], at [readAtMs], whose board names [others] and the player. */
    private suspend fun read(
        day: LocalDate,
        profile: String = "me",
        others: List<String> = listOf("friend"),
        readAtMs: Long = 5_000L,
        game: GameId = zip,
    ) {
        val rows = others.map { row(it) } + row(profile, name = "Player $profile")
        writer.write(game, day, today, profile, LinkedInBoard(rows), readAtMs)
    }

    private suspend fun saved(day: LocalDate, linked: String? = null, game: GameId = zip): SavedBoardDays.Saved =
        reader.observe(game, day, linked).first()

    @Test
    fun `a disconnected player sees a 30-day-old saved board`() = runTest {
        val old = today.minusDays(30)
        read(old, readAtMs = 7_000L)

        assertEquals(
            SavedBoardDays.Saved(7_000L, complete = true, ghosts = listOf(ghost("friend")), owner = LinkedInOwner("Player me")),
            saved(old, linked = null),
        )
    }

    @Test
    fun `a day read the day it was played is saved as partial`() = runTest {
        read(today, readAtMs = 8_000L)

        val board = saved(today)

        assertEquals(8_000L, board.readAtMs)
        assertEquals(false, board.complete)
    }

    @Test
    fun `a later read of another day leaves the first board as it was`() = runTest {
        val first = today.minusDays(30)
        read(first, readAtMs = 7_000L)
        val before = saved(first)

        read(today.minusDays(1), others = listOf("rival"), readAtMs = 9_000L)
        read(today, others = listOf("rival", "friend"), readAtMs = 10_000L)

        assertEquals(before, saved(first))
        assertEquals(listOf(ghost("rival")), saved(today.minusDays(1)).ghosts)
    }

    @Test
    fun `a day nothing was read for is not saved`() = runTest {
        read(today.minusDays(2))

        assertEquals(SavedBoardDays.Saved(null, complete = false, ghosts = emptyList(), owner = null), saved(today.minusDays(3)))
        assertEquals(SavedBoardDays.Saved(null, complete = false, ghosts = emptyList(), owner = null), saved(today.minusDays(2), game = GameId("queens")))
    }

    @Test
    fun `a board that was read and came back empty is still a saved board`() = runTest {
        writer.write(zip, today.minusDays(2), today, "me", LinkedInBoard(emptyList()), 6_000L)

        assertEquals(SavedBoardDays.Saved(6_000L, complete = true, ghosts = emptyList(), owner = null), saved(today.minusDays(2)))
    }

    @Test
    fun `the player's own row is told apart by the board's profile, not by the link`() = runTest {
        val day = today.minusDays(5)
        read(day, profile = "old", others = listOf("friend", "new"))

        // Linked to someone else, or to no one: the board still has its own player, and the linked account is one of the connections.
        for (linked in listOf("new", null)) {
            val board = saved(day, linked)
            assertEquals(LinkedInOwner("Player old"), board.owner)
            assertEquals(listOf("friend", "new"), board.ghosts.map { it.memberRef })
        }
    }

    @Test
    fun `when two profiles read the same day the one read last is the board`() = runTest {
        val day = today.minusDays(5)
        read(day, profile = "first", others = listOf("friend"), readAtMs = 5_000L)
        read(day, profile = "second", others = listOf("friend", "rival"), readAtMs = 6_000L)

        val board = saved(day, linked = "second")

        assertEquals(6_000L, board.readAtMs)
        assertEquals(LinkedInOwner("Player second"), board.owner)
        assertEquals(listOf("friend", "rival"), board.ghosts.map { it.memberRef })
        // The linked profile's own read is the one that is kept: nothing of the other's is left to show beside it.
        assertEquals(board, saved(day, linked = "first"))
    }

    @Test
    fun `a day with no board shows the linked player's name from their newest row, and nothing without a link`() = runTest {
        read(today.minusDays(9), profile = "me")

        assertEquals(
            SavedBoardDays.Saved(null, complete = false, ghosts = emptyList(), owner = LinkedInOwner("Player me")),
            saved(today.minusDays(1), linked = "me"),
        )
        assertEquals(
            SavedBoardDays.Saved(null, complete = false, ghosts = emptyList(), owner = null),
            saved(today.minusDays(1), linked = null),
        )
    }

    @Test
    fun `another account's board is hidden, the visible one's and an ownerless one are shown`() = runTest {
        val mine = today.minusDays(3)
        val theirs = today.minusDays(2)
        val ownerless = today.minusDays(1)
        owners.writerUid = "alice"
        read(mine, others = listOf("a"))
        owners.writerUid = "bob"
        read(theirs, others = listOf("b"))
        owners.writerUid = null
        read(ownerless, others = listOf("c"))
        owners.visibleOwner = "alice"

        assertEquals(listOf(ghost("a")), saved(mine).ghosts)
        assertNull(saved(theirs).readAtMs)
        assertEquals(emptyList<LinkedInGhost>(), saved(theirs).ghosts)
        assertEquals(listOf(ghost("c")), saved(ownerless).ghosts)

        owners.visibleOwner = null
        assertEquals(listOf(ghost("b")), saved(theirs).ghosts)
    }

    @Test
    fun `the owner's name stands in only from a board the visible account may see`() = runTest {
        owners.writerUid = "bob"
        read(today.minusDays(9), profile = "me", readAtMs = 1_000L)
        owners.visibleOwner = "alice"

        assertNull(saved(today.minusDays(1), linked = "me").owner)
    }

    @Test
    fun `a change of the visible owner re-emits the day`() = runTest {
        val day = today.minusDays(4)
        owners.writerUid = "alice"
        read(day, others = listOf("a"))
        owners.visibleOwner = "alice"

        reader.observe(zip, day, null).test {
            assertEquals(listOf(ghost("a")), awaitItem().ghosts)

            owners.visibleOwner = "bob"
            assertNull(awaitItem().readAtMs)

            owners.visibleOwner = "alice"
            assertEquals(listOf(ghost("a")), awaitItem().ghosts)
        }
    }

    @Test
    fun `a board written while watching shows up`() = runTest {
        val day = today.minusDays(4)

        reader.observe(zip, day, null).test {
            assertNull(awaitItem().readAtMs)

            read(day, readAtMs = 11_000L)
            var board = awaitItem()
            while (board.readAtMs == null || board.ghosts.isEmpty()) board = awaitItem()
            assertEquals(11_000L, board.readAtMs)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

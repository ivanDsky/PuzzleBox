package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import com.ivandsky.puzzlebox.database.linkedin.GhostDao
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.database.linkedin.SavedBoardDao
import com.ivandsky.puzzlebox.database.linkedin.SavedBoardEntity
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedInGhostWriterTest {

    private lateinit var db: LinkedInDatabase
    private lateinit var dao: GhostDao
    private lateinit var savedBoards: SavedBoardDao
    private lateinit var writer: LinkedInGhostWriter
    private val owner = FakeOwnerScope()

    private val game = GameId("queens")
    private val otherGame = GameId("zip")
    private val today = LocalDate.of(2026, 11, 10)
    private val ownProfileId = "own"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        dao = db.ghostDao()
        savedBoards = db.savedBoardDao()
        writer = LinkedInGhostWriter(db, owner)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(i: Int, timeSeconds: Int? = i) = LinkedInBoard.Row(
        memberRef = "m$i",
        name = "Member $i",
        timeSeconds = timeSeconds,
        hintFree = true,
        flawless = false,
    )

    private fun ghost(day: LocalDate, memberRef: String, game: GameId = this.game) = GhostEntity(
        game = game.value,
        day = day.toString(),
        memberRef = memberRef,
        name = memberRef,
        timeMs = 1_000L,
        hintFree = true,
        flawless = false,
        fetchedAtMs = 1L,
    )

    private suspend fun stored(day: LocalDate, game: GameId = this.game): List<GhostEntity> =
        dao.observe(game.value, day.toString()).first()

    private suspend fun write(board: LinkedInBoard, day: LocalDate = today, game: GameId = this.game, fetchedAtMs: Long = 5L) =
        writer.write(game, day, today, ownProfileId, board, fetchedAtMs)

    @Test
    fun `a 60-row board stores 50 rows, the first 50 in board order`() = runTest {
        write(LinkedInBoard((1..60).map { row(it) }))

        val refs = stored(today).map { it.memberRef }
        assertEquals((1..50).map { "m$it" }.toSet(), refs.toSet())
        assertEquals(50, refs.size)
    }

    @Test
    fun `the player's own row is kept next to the first 50 connections, not among them`() = runTest {
        val rows = (1..51).map { row(it) }.toMutableList()
        rows.add(10, row(10).copy(memberRef = ownProfileId, name = "Me"))
        assertEquals(52, rows.size)

        val kept = write(LinkedInBoard(rows))

        val stored = stored(today)
        val connections = stored.filter { it.memberRef != ownProfileId }
        assertEquals((1..50).map { "m$it" }.toSet(), connections.map { it.memberRef }.toSet())
        assertEquals(50, connections.size)
        assertEquals("Me", stored.single { it.memberRef == ownProfileId }.name)
        assertEquals(51, kept.size)
        assertTrue(kept.any { it.memberRef == ownProfileId })
    }

    @Test
    fun `a board without the player's row stores no own row`() = runTest {
        write(LinkedInBoard(listOf(row(1), row(2))))

        assertEquals(setOf("m1", "m2"), stored(today).map { it.memberRef }.toSet())
    }

    @Test
    fun `a hidden time stays null and a visible time is stored in milliseconds`() = runTest {
        write(
            LinkedInBoard(
                listOf(
                    row(1, timeSeconds = null).copy(hintFree = false, flawless = true),
                    row(2, timeSeconds = 83),
                ),
            ),
            fetchedAtMs = 777L,
        )

        val byRef = stored(today).associateBy { it.memberRef }
        val hidden = byRef.getValue("m1")
        assertNull(hidden.timeMs)
        assertEquals(false, hidden.hintFree)
        assertEquals(true, hidden.flawless)
        assertEquals("Member 1", hidden.name)
        assertEquals(777L, hidden.fetchedAtMs)
        val visible = byRef.getValue("m2")
        assertEquals(83_000L, visible.timeMs)
        assertEquals(true, visible.hintFree)
        assertEquals(false, visible.flawless)
        assertEquals(game.value, visible.game)
        assertEquals(today.toString(), visible.day)
    }

    @Test
    fun `a refreshed board replaces the old one for that game and day only`() = runTest {
        val yesterday = today.minusDays(1)
        dao.insertAll(listOf(ghost(today, "other-game", otherGame), ghost(yesterday, "other-day")))

        write(LinkedInBoard(listOf(row(1), row(2), row(3))))
        write(LinkedInBoard(listOf(row(1), row(3))))

        assertEquals(setOf("m1", "m3"), stored(today).map { it.memberRef }.toSet())
        assertEquals(listOf("other-game"), stored(today, otherGame).map { it.memberRef })
        assertEquals(listOf("other-day"), stored(yesterday).map { it.memberRef })
    }

    @Test
    fun `boards older than eight days are kept by a later write`() = runTest {
        val old = today.minusDays(30)
        val nine = today.minusDays(9)
        dao.insertAll(listOf(ghost(old, "old"), ghost(nine, "nine")))
        savedBoards.upsert(SavedBoardEntity(game.value, old.toString(), ownProfileId, null, 1L, true, "fp"))

        write(LinkedInBoard(listOf(row(1))))
        write(LinkedInBoard(emptyList()), day = today.minusDays(1))

        assertEquals(listOf("old"), stored(old).map { it.memberRef })
        assertEquals(listOf("nine"), stored(nine).map { it.memberRef })
        assertEquals("fp", savedBoards.get(game.value, old.toString())?.uploadedFingerprint)
    }

    @Test
    fun `a board read after its day saves a complete board with the profile, owner and read time`() = runTest {
        val day = today.minusDays(2)
        owner.writerUid = "uid-1"

        write(LinkedInBoard(listOf(row(1))), day = day, fetchedAtMs = 777L)

        assertEquals(
            SavedBoardEntity(game.value, day.toString(), ownProfileId, "uid-1", 777L, complete = true, uploadedFingerprint = null),
            savedBoards.get(game.value, day.toString()),
        )
    }

    @Test
    fun `a board read while its day runs is saved incomplete, and a signed-out read has no owner`() = runTest {
        write(LinkedInBoard(listOf(row(1))))

        val saved = savedBoards.get(game.value, today.toString())
        assertEquals(false, saved?.complete)
        assertNull(saved?.ownerUid)
    }

    @Test
    fun `a board with no rows is still a saved board`() = runTest {
        write(LinkedInBoard(emptyList()))

        assertEquals(today.toString(), savedBoards.get(game.value, today.toString())?.day)
        assertTrue(stored(today).isEmpty())
    }

    @Test
    fun `reading a board again updates its row and clears the uploaded fingerprint`() = runTest {
        val day = today.minusDays(1)
        owner.writerUid = "uid-1"
        write(LinkedInBoard(listOf(row(1))), day = day, fetchedAtMs = 10L)
        savedBoards.setFingerprint(game.value, day.toString(), "fp")

        owner.writerUid = "uid-2"
        write(LinkedInBoard(listOf(row(1), row(2))), day = day, fetchedAtMs = 20L)

        assertEquals(
            SavedBoardEntity(game.value, day.toString(), ownProfileId, "uid-2", 20L, complete = true, uploadedFingerprint = null),
            savedBoards.get(game.value, day.toString()),
        )
        assertEquals(setOf("m1", "m2"), stored(day).map { it.memberRef }.toSet())
    }

    @Test
    fun `other games and days keep their saved boards`() = runTest {
        val otherDay = today.minusDays(1)
        savedBoards.upsert(SavedBoardEntity(otherGame.value, today.toString(), ownProfileId, null, 1L, false, null))
        savedBoards.upsert(SavedBoardEntity(game.value, otherDay.toString(), ownProfileId, null, 1L, true, "fp"))

        write(LinkedInBoard(listOf(row(1))))

        assertEquals(1L, savedBoards.get(otherGame.value, today.toString())?.readAtMs)
        assertEquals("fp", savedBoards.get(game.value, otherDay.toString())?.uploadedFingerprint)
    }
}

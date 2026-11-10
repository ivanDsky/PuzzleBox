package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import com.ivandsky.puzzlebox.database.linkedin.GhostDao
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.model.GameId
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
    private lateinit var writer: LinkedInGhostWriter

    private val game = GameId("queens")
    private val otherGame = GameId("zip")
    private val today = LocalDate.of(2026, 11, 10)
    private val ownProfileId = "own"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.ghostDao()
        writer = LinkedInGhostWriter(dao)
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
    fun `the player's own row is dropped before the cap`() = runTest {
        val rows = (1..50).map { row(it) }.toMutableList()
        rows.add(10, row(10).copy(memberRef = ownProfileId, name = "Me"))
        assertEquals(51, rows.size)

        write(LinkedInBoard(rows))

        val refs = stored(today).map { it.memberRef }
        assertEquals(50, refs.size)
        assertTrue(ownProfileId !in refs)
        assertEquals((1..50).map { "m$it" }.toSet(), refs.toSet())
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
    fun `every write prunes days older than today minus 8`() = runTest {
        val nine = today.minusDays(9)
        val eight = today.minusDays(8)
        dao.insertAll(listOf(ghost(nine, "old"), ghost(eight, "edge")))

        write(LinkedInBoard(listOf(row(1))))

        assertTrue(stored(nine).isEmpty())
        assertEquals(listOf("edge"), stored(eight).map { it.memberRef })

        dao.insertAll(listOf(ghost(nine, "old")))
        write(LinkedInBoard(emptyList()))

        assertTrue(stored(nine).isEmpty())
        assertEquals(listOf("edge"), stored(eight).map { it.memberRef })
    }
}

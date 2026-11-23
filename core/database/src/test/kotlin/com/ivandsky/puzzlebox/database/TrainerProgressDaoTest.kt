package com.ivandsky.puzzlebox.database

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The trainer_progress store over real in-memory Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainerProgressDaoTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var dao: TrainerProgressDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        dao = db.trainerProgressDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(
        owner: String?,
        technique: String = "line-lock",
        game: String = "queens",
        mark: String = "practicing",
        inRow: Int = 1,
        answeredAtMs: Long = 1_000L,
        uploaded: Boolean = false,
    ) = TrainerProgressEntity(
        ownerUid = owner,
        gameId = game,
        techniqueId = technique,
        mark = mark,
        inRow = inRow,
        answeredAtMs = answeredAtMs,
        uploaded = uploaded,
    )

    private suspend fun everyRow(game: String = "queens") = dao.observe(game, null).first()

    @Test
    fun `upsert keeps one row per owner game and technique, the ownerless row included`() = runTest {
        dao.upsert(row(owner = null, inRow = 1))
        dao.upsert(row(owner = null, inRow = 2))
        dao.upsert(row(owner = "a", inRow = 3))
        dao.upsert(row(owner = "a", inRow = 4))

        assertEquals(listOf<Pair<String?, Int>>(null to 2, "a" to 4), everyRow().map { it.ownerUid to it.inRow })
    }

    @Test
    fun `upsert keeps the row id and separates games and techniques`() = runTest {
        dao.upsert(row(owner = "a", inRow = 1))
        val id = dao.find("queens", "line-lock", "a")!!.id
        dao.upsert(row(owner = "a", inRow = 2))
        dao.upsert(row(owner = "a", technique = "pairs"))
        dao.upsert(row(owner = "a", game = "tango"))

        assertEquals(id, dao.find("queens", "line-lock", "a")!!.id)
        assertEquals(2, dao.find("queens", "line-lock", "a")!!.inRow)
        assertEquals(2, everyRow("queens").size)
        assertEquals(1, everyRow("tango").size)
    }

    @Test
    fun `find matches the writer's own row exactly`() = runTest {
        dao.upsert(row(owner = null, inRow = 1))
        dao.upsert(row(owner = "a", inRow = 2))

        assertEquals(1, dao.find("queens", "line-lock", null)!!.inRow)
        assertEquals(2, dao.find("queens", "line-lock", "a")!!.inRow)
        assertNull(dao.find("queens", "line-lock", "b"))
        assertNull(dao.find("queens", "pairs", "a"))
    }

    @Test
    fun `an owner sees its own rows and the ownerless ones, never another owner's`() = runTest {
        dao.upsert(row(owner = null, technique = "t-none"))
        dao.upsert(row(owner = "a", technique = "t-a"))
        dao.upsert(row(owner = "b", technique = "t-b"))

        assertEquals(setOf("t-none", "t-a", "t-b"), dao.observe("queens", null).first().map { it.techniqueId }.toSet())
        assertEquals(setOf("t-none", "t-a"), dao.observe("queens", "a").first().map { it.techniqueId }.toSet())
        assertEquals(setOf("t-none", "t-b"), dao.observe("queens", "b").first().map { it.techniqueId }.toSet())
        assertEquals(setOf("t-none"), dao.observe("queens", "c").first().map { it.techniqueId }.toSet())
    }

    @Test
    fun `observe reads one game only`() = runTest {
        dao.upsert(row(owner = "a", game = "queens"))
        dao.upsert(row(owner = "a", game = "tango"))

        assertEquals(listOf("tango"), dao.observe("tango", "a").first().map { it.gameId })
    }

    @Test
    fun `a write that changes the count marks the row not uploaded`() = runTest {
        dao.insert(row(owner = "a", uploaded = true))
        assertTrue(dao.find("queens", "line-lock", "a")!!.uploaded)

        dao.upsert(row(owner = "a", inRow = 2, uploaded = true))

        assertFalse(dao.find("queens", "line-lock", "a")!!.uploaded)
    }

    @Test
    fun `a write that changes the mark marks the row not uploaded`() = runTest {
        dao.insert(row(owner = "a", mark = "practicing", inRow = 4, uploaded = true))

        dao.upsert(row(owner = "a", mark = "mastered", inRow = 4, uploaded = true))

        assertFalse(dao.find("queens", "line-lock", "a")!!.uploaded)
    }

    @Test
    fun `a write that changes only the answer time keeps the row's flag and takes the new time`() = runTest {
        dao.insert(row(owner = "a", answeredAtMs = 1_000L, uploaded = true))
        dao.insert(row(owner = "a", technique = "pairs", answeredAtMs = 1_000L, uploaded = false))

        dao.upsert(row(owner = "a", answeredAtMs = 5_000L))
        dao.upsert(row(owner = "a", technique = "pairs", answeredAtMs = 5_000L))

        val sent = dao.find("queens", "line-lock", "a")!!
        assertTrue(sent.uploaded)
        assertEquals(5_000L, sent.answeredAtMs)
        val unsent = dao.find("queens", "pairs", "a")!!
        assertFalse(unsent.uploaded)
        assertEquals(5_000L, unsent.answeredAtMs)
    }

    @Test
    fun `a new row starts not uploaded`() = runTest {
        dao.upsert(row(owner = "a", uploaded = true))

        assertFalse(dao.find("queens", "line-lock", "a")!!.uploaded)
    }

    @Test
    fun `change hands over the rows of that technique the owner sees and writes what it returns`() = runTest {
        dao.upsert(row(owner = null, technique = "line-lock", inRow = 1))
        dao.upsert(row(owner = "b", technique = "line-lock", inRow = 9))
        dao.upsert(row(owner = "a", technique = "pairs", inRow = 7))
        var seen: List<Pair<String?, Int>> = emptyList()

        val written = dao.change("queens", "line-lock", "a") { visible ->
            seen = visible.map { it.ownerUid to it.inRow }
            row(owner = "a", inRow = 5)
        }

        assertEquals(listOf<Pair<String?, Int>>(null to 1), seen)
        assertEquals(5, written.inRow)
        assertEquals(5, dao.find("queens", "line-lock", "a")!!.inRow)
        assertEquals(1, dao.find("queens", "line-lock", null)!!.inRow)
        assertEquals(9, dao.find("queens", "line-lock", "b")!!.inRow)
    }

    @Test
    fun `pendingGames holds every row of the owner's games that have a pending row, and no other`() = runTest {
        dao.insert(row(owner = "a", game = "queens", technique = "line-lock", uploaded = false))
        dao.insert(row(owner = "a", game = "queens", technique = "pairs", uploaded = true))
        dao.insert(row(owner = "a", game = "tango", technique = "pairs", uploaded = true))
        dao.insert(row(owner = "b", game = "tango", technique = "pairs", uploaded = false))
        dao.insert(row(owner = null, game = "tango", technique = "loose", uploaded = false))

        assertEquals(
            listOf("queens/line-lock", "queens/pairs"),
            dao.pendingGames("a").map { "${it.gameId}/${it.techniqueId}" },
        )
        assertEquals(listOf("tango/pairs"), dao.pendingGames("b").map { "${it.gameId}/${it.techniqueId}" })
        assertTrue(dao.pendingGames("c").isEmpty())
    }

    @Test
    fun `observePendingGames follows a game turning pending and leaving once it is marked uploaded`() = runTest {
        dao.insert(row(owner = "a", uploaded = true))
        assertTrue(dao.observePendingGames("a").first().isEmpty())

        dao.upsert(row(owner = "a", inRow = 2))
        assertEquals(listOf(2), dao.observePendingGames("a").first().map { it.inRow })

        dao.markUploaded("a", "queens", "line-lock", "practicing", 2, 1_000L)
        assertTrue(dao.observePendingGames("a").first().isEmpty())
    }

    @Test
    fun `markUploaded marks the row only while it still holds the value that was sent`() = runTest {
        dao.insert(row(owner = "a", mark = "practicing", inRow = 2, answeredAtMs = 1_000L))

        assertEquals(0, dao.markUploaded("a", "queens", "line-lock", "practicing", 1, 1_000L))
        assertEquals(0, dao.markUploaded("a", "queens", "line-lock", "mastered", 2, 1_000L))
        assertEquals(0, dao.markUploaded("a", "queens", "line-lock", "practicing", 2, 999L))
        assertEquals(0, dao.markUploaded("b", "queens", "line-lock", "practicing", 2, 1_000L))
        assertFalse(dao.find("queens", "line-lock", "a")!!.uploaded)

        assertEquals(1, dao.markUploaded("a", "queens", "line-lock", "practicing", 2, 1_000L))
        assertTrue(dao.find("queens", "line-lock", "a")!!.uploaded)
    }

    @Test
    fun `clearUploaded clears the owner's flags only`() = runTest {
        dao.insert(row(owner = "a", uploaded = true))
        dao.insert(row(owner = "b", uploaded = true))
        dao.insert(row(owner = null, technique = "loose", uploaded = true))

        dao.clearUploaded("a")

        assertFalse(dao.find("queens", "line-lock", "a")!!.uploaded)
        assertTrue(dao.find("queens", "line-lock", "b")!!.uploaded)
        assertTrue(dao.find("queens", "loose", null)!!.uploaded)
    }

    @Test
    fun `restore keeps the flag it is given and the stored row's id`() = runTest {
        dao.insert(row(owner = "a", inRow = 1, uploaded = false))
        val id = dao.find("queens", "line-lock", "a")!!.id

        dao.restore(row(owner = "a", inRow = 3, uploaded = true))
        dao.restore(row(owner = "a", technique = "pairs", inRow = 1, uploaded = true))

        val restored = dao.find("queens", "line-lock", "a")!!
        assertEquals(id, restored.id)
        assertEquals(3, restored.inRow)
        assertTrue(restored.uploaded)
        assertTrue(dao.find("queens", "pairs", "a")!!.uploaded)
        assertEquals(1, dao.ownedRowsOfGame("a", "queens").count { it.techniqueId == "line-lock" })
    }

    @Test
    fun `adopt folds each ownerless row into the owner's row, deletes it and leaves the result pending`() = runTest {
        dao.insert(row(owner = null, technique = "line-lock", inRow = 4))
        dao.insert(row(owner = null, technique = "solo", game = "tango", inRow = 1))
        dao.insert(row(owner = "a", technique = "line-lock", inRow = 2, uploaded = true))
        dao.insert(row(owner = "b", technique = "line-lock", inRow = 9, uploaded = true))
        val seen = mutableListOf<Pair<Int?, Int>>()

        dao.adopt("a") { owned, loose ->
            seen += owned?.inRow to loose.inRow
            loose.copy(inRow = loose.inRow + (owned?.inRow ?: 0))
        }

        assertEquals(setOf<Pair<Int?, Int>>(2 to 4, null to 1), seen.toSet())
        assertTrue(dao.ownerlessRows().isEmpty())
        val folded = dao.find("queens", "line-lock", "a")!!
        assertEquals(6, folded.inRow)
        assertFalse(folded.uploaded)
        assertEquals(1, dao.ownedRowsOfGame("a", "queens").size)
        val created = dao.find("tango", "solo", "a")!!
        assertEquals(1, created.inRow)
        assertFalse(created.uploaded)
        assertEquals(9, dao.find("queens", "line-lock", "b")!!.inRow)
        assertTrue(dao.find("queens", "line-lock", "b")!!.uploaded)
    }

    @Test
    fun `adopt with nothing ownerless changes nothing`() = runTest {
        dao.insert(row(owner = "a", uploaded = true))

        dao.adopt("a") { _, loose -> loose }

        assertTrue(dao.find("queens", "line-lock", "a")!!.uploaded)
    }
}

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
        uploaded: Boolean = false,
    ) = TrainerProgressEntity(
        ownerUid = owner,
        gameId = game,
        techniqueId = technique,
        mark = mark,
        inRow = inRow,
        answeredAtMs = 1_000L,
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
    fun `a write marks the row not uploaded`() = runTest {
        dao.insert(row(owner = "a", uploaded = true))
        assertTrue(dao.find("queens", "line-lock", "a")!!.uploaded)

        dao.upsert(row(owner = "a", inRow = 2, uploaded = true))

        assertFalse(dao.find("queens", "line-lock", "a")!!.uploaded)
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
}

package com.ivandsky.puzzlebox.database

import androidx.room.Room
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

/** The self_matches and self_past_legs store over real in-memory Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelfMatchDaoTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var dao: SelfMatchDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        dao = db.selfMatchDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun match(code: String, createdAtMs: Long = 1L) =
        SelfMatchEntity(code, createdAtMs, roundCount = 3, games = "zip,sudoku", includeFriends = true, legs = "a|b|c")

    private fun legs(code: String) = (0..2).map { SelfPastLegEntity(code, it, 60_000L + it, it, 9_000L + it) }

    @Test
    fun `a stored self duel reads back with its settings and past legs in leg order`() = runTest {
        dao.insert(match("SELF01"), legs("SELF01").reversed())

        assertEquals(match("SELF01"), dao.match("SELF01"))
        assertEquals(match("SELF01"), dao.observeMatch("SELF01").first())
        assertEquals(legs("SELF01"), dao.pastLegs("SELF01"))
        assertEquals(legs("SELF01"), dao.observePastLegs("SELF01").first())
    }

    @Test
    fun `an unknown code reads as absent`() = runTest {
        assertNull(dao.match("NOPE00"))
        assertNull(dao.observeMatch("NOPE00").first())
        assertEquals(emptyList<SelfPastLegEntity>(), dao.pastLegs("NOPE00"))
    }

    @Test
    fun `delete removes the match and its past legs but leaves other duels`() = runTest {
        dao.insert(match("SELF01"), legs("SELF01"))
        dao.insert(match("SELF02"), legs("SELF02"))

        dao.delete("SELF01")

        assertNull(dao.match("SELF01"))
        assertEquals(emptyList<SelfPastLegEntity>(), dao.pastLegs("SELF01"))
        assertEquals(legs("SELF02"), dao.pastLegs("SELF02"))
    }

    @Test
    fun `observeAll lists self duels newest first`() = runTest {
        dao.insert(match("OLD001", createdAtMs = 1L), legs("OLD001"))
        dao.insert(match("NEW001", createdAtMs = 2L), legs("NEW001"))

        assertEquals(listOf("NEW001", "OLD001"), dao.observeAll().first().map { it.code })
    }
}

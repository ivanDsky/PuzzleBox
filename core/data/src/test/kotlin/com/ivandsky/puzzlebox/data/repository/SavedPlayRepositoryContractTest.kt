package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Runs [SavedPlayRepositoryContract] against both adapters: [RoomSavedPlayRepository] over
 * real in-memory Room (with a ticking clock) and [FakeSavedPlayRepository]. The 9th-save
 * eviction case is Room-only, since the cap is not on the port.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class SavedPlayRepositoryContractTest(private val adapter: String) : SavedPlayRepositoryContract() {

    private var db: PuzzleBoxDatabase? = null
    private var nowMs = 0L

    override val repository: SavedPlayRepository by lazy {
        when (adapter) {
            ROOM -> {
                val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
                db = database
                RoomSavedPlayRepository(database.savedPlayDao(), clock = { ++nowMs })
            }
            else -> FakeSavedPlayRepository()
        }
    }

    @After
    fun tearDown() {
        db?.close()
    }

    @Test
    fun `room adapter's ninth save prunes the oldest`() = runTest {
        assumeTrue(adapter == ROOM)
        for (day in 1..9) repository.save(slot("zip", day), byteArrayOf(day.toByte()))

        assertNull(repository.load(slot("zip", 1)))
        assertEquals((2..9).map { slot("zip", it) }.toSet(), repository.observeInProgress().first())
    }

    companion object {
        private const val ROOM = "room"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun adapters(): List<Array<Any>> = listOf(arrayOf(ROOM), arrayOf("fake"))
    }
}

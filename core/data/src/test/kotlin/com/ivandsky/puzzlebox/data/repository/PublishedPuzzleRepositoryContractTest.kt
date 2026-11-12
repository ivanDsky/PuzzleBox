package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Runs [PublishedPuzzleRepositoryContract] on [RoomPublishedPuzzleRepository] over in-memory Room
 * and on [FakePublishedPuzzleRepository].
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class PublishedPuzzleRepositoryContractTest(private val adapter: String) : PublishedPuzzleRepositoryContract() {

    private var db: PuzzleBoxDatabase? = null

    override val repository: PublishedPuzzleRepository by lazy {
        when (adapter) {
            ROOM -> {
                val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
                db = database
                RoomPublishedPuzzleRepository(database.publishedPuzzleDao(), FakeOwnerScope())
            }
            else -> FakePublishedPuzzleRepository()
        }
    }

    @After
    fun tearDown() {
        db?.close()
    }

    companion object {
        private const val ROOM = "room"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun adapters(): List<Array<Any>> = listOf(arrayOf(ROOM), arrayOf("fake"))
    }
}

package com.ivandsky.puzzlebox.data.rankhistory

import androidx.room.Room
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.testing.contract.RankHistoryStoreContract
import com.ivandsky.puzzlebox.testing.repository.FakeRankHistoryStore
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Runs [RankHistoryStoreContract] on [RoomRankHistoryStore] over in-memory Room and on [FakeRankHistoryStore]. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class RankHistoryStoreContractTest(private val adapter: String) : RankHistoryStoreContract() {

    private var db: PuzzleBoxDatabase? = null

    override val store: RankHistoryStore by lazy {
        when (adapter) {
            ROOM -> {
                val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
                    .allowMainThreadQueries()
                    .setQueryExecutor(RoomQueryThread.executor)
                    .build()
                db = database
                RoomRankHistoryStore(database.rankHistoryDao())
            }
            else -> FakeRankHistoryStore()
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

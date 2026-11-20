package com.ivandsky.puzzlebox.data.trainer

import androidx.room.Room
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.TrainerPuzzleEntity
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import java.time.LocalDate
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [CachedTrainerPuzzleSource]: the cached dailies newest day first, then the Trainer's own fetched puzzles newest
 * fetch first, from the device only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CachedTrainerPuzzleSourceTest {

    private val game: GameId = TestGames.alpha
    private val older = LocalDate.of(2026, 11, 16)
    private val middle = LocalDate.of(2026, 11, 17)
    private val newest = LocalDate.of(2026, 11, 18)
    private val daily = FakeDailyRepository()
    private lateinit var db: PuzzleBoxDatabase
    private lateinit var source: CachedTrainerPuzzleSource

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        source = CachedTrainerPuzzleSource(daily, db.trainerPuzzleDao(), TestGames.registry(listOf(TestGames.alpha, TestGames.beta)))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(day: LocalDate) = DailyEntry(game, day, puzzleNumber = null, variant = "alpha/v1", fetchedAtMs = 0)

    private suspend fun store(
        puzzle: Puzzle,
        number: Int,
        fetchedAtMs: Long,
        game: GameId = this.game,
        payload: ByteArray = puzzle.payload,
    ) {
        db.trainerPuzzleDao().insert(
            TrainerPuzzleEntity(game.value, number, "2026-11-01", puzzle.variant.value, puzzle.formatVersion, payload, fetchedAtMs),
        )
    }

    /**
     * Bug caught: the dailies' newest-first order lost (the round would cut from the oldest day first).
     */
    @Test
    fun `puzzles come newest day first`() = runTest {
        val oldPuzzle = TestGames.puzzle(game, tag = 1)
        val midPuzzle = TestGames.puzzle(game, tag = 2)
        val newPuzzle = TestGames.puzzle(game, tag = 3)
        daily.setHistory(game, listOf(entry(older), entry(newest), entry(middle)))
        daily.setCached(game, older, oldPuzzle)
        daily.setCached(game, middle, midPuzzle)
        daily.setCached(game, newest, newPuzzle)

        assertEquals(listOf(newPuzzle, midPuzzle, oldPuzzle), source.puzzles(game).toList())
    }

    /**
     * Bug caught: a history entry whose puzzle was evicted aborts the stream or emits a stand-in.
     */
    @Test
    fun `a day whose puzzle is no longer cached is skipped`() = runTest {
        val oldPuzzle = TestGames.puzzle(game, tag = 1)
        val newPuzzle = TestGames.puzzle(game, tag = 3)
        daily.setHistory(game, listOf(entry(older), entry(middle), entry(newest)))
        daily.setCached(game, older, oldPuzzle)
        daily.setCached(game, newest, newPuzzle)

        assertEquals(listOf(newPuzzle, oldPuzzle), source.puzzles(game).toList())
    }

    /**
     * Bug caught: a read that refreshes today or fills a missing day, so opening a round touches the network.
     */
    @Test
    fun `reading never fetches`() = runTest {
        daily.setHistory(game, listOf(entry(middle), entry(newest)))
        daily.setCached(game, newest, TestGames.puzzle(game))
        store(TestGames.puzzle(game, tag = 7), number = 7, fetchedAtMs = 1)

        source.puzzles(game).toList()

        assertEquals("refreshToday would fetch today", 0, daily.todayCalls(game))
        assertEquals("day() would fetch a miss", 0, daily.dayCalls(game, newest))
        assertEquals("day() would fetch a miss", 0, daily.dayCalls(game, middle))
    }

    /**
     * Bug caught: a game with nothing on the device getting a puzzle from somewhere, or the stream failing.
     */
    @Test
    fun `a game with no captured day gives no puzzle`() = runTest {
        assertEquals(emptyList<Any>(), source.puzzles(game).toList())
    }

    /**
     * Bug caught: a fetched puzzle invisible to rounds (A7), a daily pushed behind a fetched one, or the
     * fetched ones in number order instead of newest fetch first.
     */
    @Test
    fun `fetched puzzles follow the dailies, newest fetch first`() = runTest {
        val dailyPuzzle = TestGames.puzzle(game, tag = 1)
        val first = TestGames.puzzle(game, tag = 11)
        val second = TestGames.puzzle(game, tag = 12)
        val third = TestGames.puzzle(game, tag = 13)
        daily.setHistory(game, listOf(entry(newest)))
        daily.setCached(game, newest, dailyPuzzle)
        store(first, number = 30, fetchedAtMs = 100)
        store(third, number = 10, fetchedAtMs = 300)
        store(second, number = 20, fetchedAtMs = 200)

        assertEquals(listOf(dailyPuzzle, third, second, first), source.puzzles(game).toList())
    }

    /**
     * Bug caught: another game's fetched puzzle in this game's round, or one row that no longer decodes ending
     * the stream.
     */
    @Test
    fun `other games and undecodable rows are left out`() = runTest {
        val mine = TestGames.puzzle(game, tag = 1)
        store(TestGames.puzzle(TestGames.beta, tag = 2), number = 5, fetchedAtMs = 500, game = TestGames.beta)
        store(mine, number = 6, fetchedAtMs = 100)
        store(mine, number = 7, fetchedAtMs = 400, payload = byteArrayOf(0, 1))

        assertEquals(listOf(mine), source.puzzles(game).toList())
    }
}

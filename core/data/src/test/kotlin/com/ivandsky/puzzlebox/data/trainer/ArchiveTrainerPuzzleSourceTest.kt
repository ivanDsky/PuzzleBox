package com.ivandsky.puzzlebox.data.trainer

import androidx.room.Room
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.linkedin.LinkedInCallBudget
import com.ivandsky.puzzlebox.data.trainer.ArchiveLoad.CantLoad.Reason
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.TrainerPuzzleEntity
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.FakeDailySource
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [ArchiveTrainerPuzzleSource] over in-memory Room, a [FakeDailySource] that records every request and a fake
 * walker: what one tap fetches, what it costs, where the puzzle lands, and what a rejected puzzle leaves behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveTrainerPuzzleSourceTest {

    private val game = TestGames.alpha
    private val lineLock = TechniqueId("line-lock")
    private val pairs = TechniqueId("pairs")
    private val day1 = LocalDate.of(2026, 1, 1)
    private val fake = FakeDailySource(game)
    private var now = 5_000L
    private lateinit var db: PuzzleBoxDatabase

    /** The numbers [dayOf] knows: 1 through 1000; anything else is a number the source cannot place. */
    private val daily: DailySource = object : DailySource by fake {
        override fun dayOf(number: Int): LocalDate? = if (number in 1..1000) day1.plusDays(number - 1L) else null
    }

    /** What the walker reports per puzzle tag (the second payload byte); a tag it does not list uses no technique. */
    private val found = mutableMapOf<Byte, Map<TechniqueId, Int>>()
    private val walker = TechniqueWalker { puzzle -> found[puzzle.payload[1]].orEmpty() }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        found[TAG] = mapOf(lineLock to 3)
        fake.nextPuzzle = TestGames.puzzle(game, TAG)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun archive(
        budget: LinkedInCallBudget = LinkedInCallBudget(nowMs = { 0L }, limit = 100),
        sources: Map<GameId, DailySource> = mapOf(game to daily),
        walker: TechniqueWalker? = this.walker,
    ): ArchiveTrainerPuzzleSource {
        val base = TestGames.plugin(game)
        val plugin = GamePlugin(
            base.definition, base.rules, base.session, base.score, base.stateOf, base.initialSession, base.refOf,
            base.playAgainSession, base.rendererModel, techniqueWalker = walker,
        )
        val dispatcher = UnconfinedTestDispatcher()
        val dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        }
        return ArchiveTrainerPuzzleSource(sources, GameRegistry(listOf(plugin)), db.trainerPuzzleDao(), budget, dispatchers) { now }
    }

    private suspend fun store(number: Int, tag: Byte, payload: ByteArray = byteArrayOf(TestGames.MAGIC, tag)) {
        db.trainerPuzzleDao().insert(
            TrainerPuzzleEntity(game.value, number, day1.plusDays(number - 1L).toString(), TestGames.variant(game).value, 1, payload, 1L),
        )
    }

    private fun puzzle(tag: Byte): Puzzle = TestGames.puzzle(game, tag)

    /**
     * Bug caught: a load that fetches more than the tapped number (a prefetch, a retry loop, a neighbouring day)
     * or reaches the feed through `fetchDaily` instead of the by-date `fetchPast`.
     */
    @Test
    fun `one load makes exactly one by-date fetch of that number's day`() = runTest {
        val result = archive().load(game, lineLock, 42)

        assertEquals(ArchiveLoad.Loaded(42, puzzle(TAG)), result)
        assertEquals(listOf(day1.plusDays(41)), fake.requestedPastDates)
        assertEquals("fetchDaily only through fetchPast", 1, fake.requestedDates.size)
    }

    /**
     * Bug caught: a cached puzzle fetched again (a scarce call spent, a dead cache offline), or the fetch of a
     * number repeated on the second tap.
     */
    @Test
    fun `a cached number loads with zero fetches, and so does a number loaded before`() = runTest {
        val archive = archive()
        store(7, TAG)

        assertEquals(ArchiveLoad.Loaded(7, puzzle(TAG)), archive.load(game, lineLock, 7))
        assertEquals(ArchiveLoad.Loaded(8, puzzle(TAG)), archive.load(game, lineLock, 8))
        assertEquals(ArchiveLoad.Loaded(8, puzzle(TAG)), archive.load(game, lineLock, 8))

        assertEquals("only 8's first load fetches", listOf(day1.plusDays(7)), fake.requestedPastDates)
    }

    /**
     * Bug caught: a load that waits for budget, or fetches anyway when it is used up, or stores nothing-ness.
     */
    @Test
    fun `budget denied gives Busy with no fetch and nothing stored`() = runTest {
        val spent = LinkedInCallBudget(nowMs = { 0L }, limit = 1).also { assertTrue(it.tryAcquire()) }

        val result = archive(budget = spent).load(game, lineLock, 3)

        assertEquals(ArchiveLoad.CantLoad(3, Reason.Busy), result)
        assertEquals(emptyList<LocalDate>(), fake.requestedDates)
        assertEquals(emptyList<Int>(), db.trainerPuzzleDao().numbers(game.value))
    }

    /**
     * Bug caught: a fetch that skips the shared call budget, so rapid taps exceed LinkedIn's cap.
     */
    @Test
    fun `a fetch takes one slot of the budget`() = runTest {
        val archive = archive(budget = LinkedInCallBudget(nowMs = { 0L }, limit = 1))

        assertEquals(ArchiveLoad.Loaded(1, puzzle(TAG)), archive.load(game, lineLock, 1))
        assertEquals(ArchiveLoad.CantLoad(2, Reason.Busy), archive.load(game, lineLock, 2))
        assertEquals("the cached number needs no slot", ArchiveLoad.Loaded(1, puzzle(TAG)), archive.load(game, lineLock, 1))
        assertEquals(1, fake.requestedPastDates.size)
    }

    /**
     * Bug caught: a feed error mapped to the wrong player state (a throttle read as gone, a trust failure as
     * offline) or one that leaves a row behind.
     */
    @Test
    fun `each fetch error maps to its reason and stores nothing`() = runTest {
        val archive = archive()
        val expected = mapOf(
            FetchError.Throttled("t") to Reason.Busy,
            FetchError.Unavailable("u") to Reason.Offline,
            FetchError.NotFound("n") to Reason.Gone,
            FetchError.Untrusted("x") to Reason.Untrusted,
        )
        fake.nextPuzzle = null

        for ((error, reason) in expected) {
            fake.nextError = error
            assertEquals(error.toString(), ArchiveLoad.CantLoad(9, reason), archive.load(game, lineLock, 9))
        }

        assertEquals("one fetch per load, no retry", expected.size, fake.requestedPastDates.size)
        assertEquals(emptyList<Int>(), db.trainerPuzzleDao().numbers(game.value))
    }

    /**
     * Bug caught: a number the source cannot place, or a game with no source, reaching the feed (a guessed day)
     * or crashing instead of reporting Gone.
     */
    @Test
    fun `a number with no day and a game with no source are Gone with no fetch`() = runTest {
        assertEquals(ArchiveLoad.CantLoad(0, Reason.Gone), archive().load(game, lineLock, 0))
        assertEquals(ArchiveLoad.CantLoad(5000, Reason.Gone), archive().load(game, lineLock, 5000))
        assertEquals(ArchiveLoad.CantLoad(5, Reason.Gone), archive(sources = emptyMap()).load(game, lineLock, 5))
        assertEquals(emptyList<LocalDate>(), fake.requestedDates)
    }

    /**
     * Bug caught: an unreadable cached row refetched (the insert would be ignored, a call wasted) or served.
     */
    @Test
    fun `a cached row that no longer decodes is Gone with no fetch`() = runTest {
        store(4, TAG, payload = byteArrayOf(0, 1))

        assertEquals(ArchiveLoad.CantLoad(4, Reason.Gone), archive().load(game, lineLock, 4))
        assertEquals(emptyList<LocalDate>(), fake.requestedDates)
    }

    /**
     * Bug caught: a puzzle without the technique used as a drill, a mismatch that is fetched again on the next
     * tap, or a fetched copy dropped on mismatch.
     */
    @Test
    fun `a mismatch is stored, skipped for the technique, and never fetched again`() = runTest {
        val archive = archive()
        found.clear()

        assertEquals(ArchiveLoad.Mismatch(11), archive.load(game, lineLock, 11))
        assertEquals("the content stays on the device", listOf(11), db.trainerPuzzleDao().numbers(game.value))
        assertEquals(ArchiveLoad.Mismatch(11), archive.load(game, lineLock, 11))
        assertEquals("skipped without a fetch", 1, fake.requestedPastDates.size)
        assertEquals(emptyList<Int>(), archive.onDevice(game, lineLock, listOf(11)))

        found[TAG] = mapOf(lineLock to 3)
        assertEquals("skipped for the session even when the walker would now agree", ArchiveLoad.Mismatch(11), archive.load(game, lineLock, 11))
    }

    /**
     * Bug caught: a skip kept per number instead of per technique: the cached copy is still a fine drill for a
     * technique the walker does find in it.
     */
    @Test
    fun `a puzzle skipped for one technique still loads for another it has`() = runTest {
        val archive = archive()
        found[TAG] = mapOf(pairs to 2)

        assertEquals(ArchiveLoad.Mismatch(11), archive.load(game, lineLock, 11))
        assertEquals(ArchiveLoad.Loaded(11, puzzle(TAG)), archive.load(game, pairs, 11))
        assertEquals(listOf(11), archive.onDevice(game, pairs, listOf(11)))
        assertEquals(emptyList<Int>(), archive.onDevice(game, lineLock, listOf(11)))
        assertEquals("the second technique used the cached copy", 1, fake.requestedPastDates.size)
    }

    /**
     * Bug caught: a cached puzzle used without the walker's say (a stale or mis-tagged index entry), or a game
     * with no walker treated as confirming everything.
     */
    @Test
    fun `a cached puzzle is confirmed too, and a game without a walker confirms nothing`() = runTest {
        store(6, tag = 99)

        assertEquals(ArchiveLoad.Mismatch(6), archive().load(game, lineLock, 6))
        store(7, TAG)
        assertEquals(ArchiveLoad.Mismatch(7), archive(walker = null).load(game, lineLock, 7))
        assertEquals(emptyList<LocalDate>(), fake.requestedDates)
    }

    /**
     * Bug caught (A16, R8): a fetched puzzle written to the daily cache, so History, the archive month and Random
     * show a puzzle the player only trained on; or a row that loses its number, day or fetch time.
     */
    @Test
    fun `a fetched puzzle lands in trainer_puzzles and the daily cache stays empty`() = runTest {
        archive().load(game, lineLock, 12)

        val row = db.trainerPuzzleDao().get(game.value, 12)!!
        assertEquals(TrainerPuzzleEntity(game.value, 12, day1.plusDays(11).toString(), TestGames.variant(game).value, 1, puzzle(TAG).payload, now), row)
        assertEquals(0, db.puzzleDailyDao().count(game.value))
        assertEquals(emptyList<Any>(), db.puzzleDailyDao().observeHistory(game.value).first())
    }

    /**
     * Bug caught: `onDevice` fetching to find out (a prefetch behind the player's back), listing numbers that
     * are not cached, or reordering the caller's most-steps-first order.
     */
    @Test
    fun `onDevice lists the cached numbers in input order and never fetches`() = runTest {
        store(1, TAG)
        store(2, TAG)
        store(3, tag = 99)
        store(4, TAG, payload = byteArrayOf(0, 1))

        val onDevice = archive().onDevice(game, lineLock, listOf(5, 2, 4, 3, 1))

        assertEquals("5 is not cached, 4 does not decode, 3 lacks the technique", listOf(2, 1), onDevice)
        assertEquals(emptyList<LocalDate>(), fake.requestedDates)
    }

    /**
     * Bug caught: a rejection found by `onDevice` forgotten, so `load` offers the same number again.
     */
    @Test
    fun `a number onDevice rejects is skipped by load`() = runTest {
        val archive = archive()
        store(3, tag = 99)

        assertEquals(emptyList<Int>(), archive.onDevice(game, lineLock, listOf(3)))
        assertEquals(ArchiveLoad.Mismatch(3), archive.load(game, lineLock, 3))
        assertEquals(emptyList<LocalDate>(), fake.requestedDates)
    }
}

private const val TAG: Byte = 7

package com.ivandsky.puzzlebox.testing.contract

import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior every [RankHistoryStore] adapter must pass. Subclasses supply a fresh, empty [store]; the Room adapter and
 * the in-memory fake both run it, so the two cannot drift. Every observation goes through the port itself.
 * Test names are camel case because this class ships in a main source set that gets dexed.
 */
abstract class RankHistoryStoreContract {

    protected abstract val store: RankHistoryStore

    private fun week(
        weekStart: String,
        ownerUid: String = ANN,
        game: GameId = ZIP,
        pointsPerDay: Double = 7.5,
        friendCount: Int = 3,
        formatVersion: Int = RankHistoryStore.FORMAT_VERSION,
    ) = RankHistoryStore.Week(ownerUid, game, LocalDate.parse(weekStart), pointsPerDay, friendCount, formatVersion)

    private suspend fun read(
        from: String = "2026-01-01",
        to: String = "2026-12-31",
        ownerUid: String = ANN,
        game: GameId = ZIP,
    ) = store.weeks(ownerUid, game, LocalDate.parse(from)..LocalDate.parse(to))

    @Test
    fun anEmptyStoreReadsNothing() = runTest {
        assertEquals(emptyList<RankHistoryStore.Week>(), read())
    }

    @Test
    fun aKeptWeekReadsBackAsKept() = runTest {
        val kept = week("2026-11-09", pointsPerDay = 6.25, friendCount = 4, formatVersion = 2)

        assertTrue(store.insertOnce(kept))

        assertEquals(listOf(kept), read())
    }

    @Test
    fun aSecondInsertForTheSameOwnerGameAndWeekLeavesTheFirstRow() = runTest {
        val first = week("2026-11-09", pointsPerDay = 7.5, friendCount = 3)

        assertTrue(store.insertOnce(first))
        assertFalse(store.insertOnce(week("2026-11-09", pointsPerDay = 1.0, friendCount = 9, formatVersion = 2)))

        assertEquals(listOf(first), read())
    }

    @Test
    fun theSameWeekIsKeptSeparatelyPerOwnerAndPerGame() = runTest {
        val mine = week("2026-11-09", pointsPerDay = 7.5)
        val theirs = week("2026-11-09", ownerUid = BOB, pointsPerDay = 2.0)
        val otherGame = week("2026-11-09", game = SUDOKU, pointsPerDay = 4.0)

        assertTrue(store.insertOnce(mine))
        assertTrue(store.insertOnce(theirs))
        assertTrue(store.insertOnce(otherGame))

        assertEquals(listOf(mine), read())
        assertEquals(listOf(theirs), read(ownerUid = BOB))
        assertEquals(listOf(otherGame), read(game = SUDOKU))
    }

    @Test
    fun aWeekIsNotReadForAnotherOwnerOrGame() = runTest {
        store.insertOnce(week("2026-11-09"))

        assertEquals(emptyList<RankHistoryStore.Week>(), read(ownerUid = BOB))
        assertEquals(emptyList<RankHistoryStore.Week>(), read(game = SUDOKU))
    }

    @Test
    fun weeksReadOldestFirstWhateverTheInsertOrder() = runTest {
        val starts = listOf("2026-11-16", "2026-11-02", "2026-11-23", "2026-11-09")
        starts.forEach { store.insertOnce(week(it)) }

        assertEquals(starts.sorted(), read().map { it.weekStart.toString() })
    }

    @Test
    fun theRangeKeepsItsFirstAndLastWeekStartAndNothingOutsideThem() = runTest {
        listOf("2026-10-26", "2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23").forEach { store.insertOnce(week(it)) }

        assertEquals(
            listOf("2026-11-02", "2026-11-09", "2026-11-16"),
            read(from = "2026-11-02", to = "2026-11-16").map { it.weekStart.toString() },
        )
    }

    @Test
    fun aRangeThatEndsBeforeItStartsReadsNothing() = runTest {
        store.insertOnce(week("2026-11-09"))

        assertEquals(emptyList<RankHistoryStore.Week>(), read(from = "2026-11-16", to = "2026-11-02"))
    }

    private companion object {
        const val ANN = "ann-uid"
        const val BOB = "bob-uid"
        val ZIP = GameId("zip")
        val SUDOKU = GameId("sudoku")
    }
}

package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CachedDailyTrainerPuzzleSourceTest {

    private val game: GameId = TestGames.alpha
    private val older = LocalDate.of(2026, 11, 16)
    private val middle = LocalDate.of(2026, 11, 17)
    private val newest = LocalDate.of(2026, 11, 18)
    private val daily = FakeDailyRepository()
    private val source = CachedDailyTrainerPuzzleSource(daily)

    private fun entry(day: LocalDate) = DailyEntry(game, day, puzzleNumber = null, variant = "alpha/v1", fetchedAtMs = 0)

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

    @Test
    fun `a day whose puzzle is no longer cached is skipped`() = runTest {
        val oldPuzzle = TestGames.puzzle(game, tag = 1)
        val newPuzzle = TestGames.puzzle(game, tag = 3)
        daily.setHistory(game, listOf(entry(older), entry(middle), entry(newest)))
        daily.setCached(game, older, oldPuzzle)
        daily.setCached(game, newest, newPuzzle)

        assertEquals(listOf(newPuzzle, oldPuzzle), source.puzzles(game).toList())
    }

    @Test
    fun `reading never fetches`() = runTest {
        daily.setHistory(game, listOf(entry(middle), entry(newest)))
        daily.setCached(game, newest, TestGames.puzzle(game))

        source.puzzles(game).toList()

        assertEquals("refreshToday would fetch today", 0, daily.todayCalls(game))
        assertEquals("day() would fetch a miss", 0, daily.dayCalls(game, newest))
        assertEquals("day() would fetch a miss", 0, daily.dayCalls(game, middle))
    }

    @Test
    fun `a game with no captured day gives no puzzle`() = runTest {
        assertEquals(emptyList<Any>(), source.puzzles(game).toList())
    }
}

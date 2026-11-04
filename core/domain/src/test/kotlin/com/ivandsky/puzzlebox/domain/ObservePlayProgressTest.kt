package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Today's progress rules (A12, A13, SC10, SC11) over the core:testing fakes. */
class ObservePlayProgressTest {

    private val gamma = GameId("gamma")
    private val today = LocalDate.of(2026, 11, 4)
    private val yesterday = today.minusDays(1)

    private val saved = FakeSavedPlayRepository()
    private val completions = FakeCompletionRepository()
    private val calendar = FakeGameCalendar(today)

    private fun progress(games: List<GameId> = listOf(alpha, beta, gamma)) =
        ObservePlayProgress(TestGames.registry(games), saved, completions, calendar)()

    private fun record(game: GameId, day: LocalDate) = CompletionRecord(
        puzzleRef = Ref(game, day),
        time = 1_000L,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        isRecord = true,
    )

    @Test
    fun `in progress counts only today's slot`() = runTest {
        saved.save(PlaySlot(alpha, today), byteArrayOf(1))
        saved.save(PlaySlot(beta, yesterday), byteArrayOf(1))

        val p = progress().first()

        assertEquals(setOf(alpha), p.inProgress)
        assertTrue(p.anyInProgress)
    }

    @Test
    fun `solved comes from today's records only`() = runTest {
        completions.setRecords(listOf(record(beta, today), record(gamma, yesterday)))

        val p = progress().first()

        assertEquals(setOf(beta), p.solved)
        assertFalse(p.anyInProgress)
    }

    @Test
    fun `order follows the registry`() = runTest {
        assertEquals(listOf(gamma, alpha, beta), progress(listOf(gamma, alpha, beta)).first().order)
    }

    @Test
    fun `nextUnsolved wraps and excludes the current game`() {
        val p = PlayProgress(listOf(alpha, beta, gamma), inProgress = emptySet(), solved = setOf(alpha))

        assertEquals(beta, p.nextUnsolved(alpha))
        assertEquals(beta, p.nextUnsolved(gamma))
        assertEquals(gamma, p.nextUnsolved(beta))
    }

    @Test
    fun `nextUnsolved is null when all others are solved`() {
        val p = PlayProgress(listOf(alpha, beta, gamma), inProgress = emptySet(), solved = setOf(alpha, beta))

        assertNull(p.nextUnsolved(gamma))
        assertNull(p.copy(solved = setOf(alpha, beta, gamma)).nextUnsolved(alpha))
    }

    @Test
    fun `a game both saved and solved reports only solved`() = runTest {
        progress().test {
            assertEquals(emptySet<GameId>(), awaitItem().inProgress)

            saved.save(PlaySlot(alpha, today), byteArrayOf(1))
            assertEquals(setOf(alpha), awaitItem().inProgress)

            completions.onCompleted(record(alpha, today))
            val solvedFirst = awaitItem()
            assertEquals(emptySet<GameId>(), solvedFirst.inProgress)
            assertEquals(setOf(alpha), solvedFirst.solved)

            // The save delete landing after the record changes nothing observable.
            saved.delete(PlaySlot(alpha, today))
            expectNoEvents()
        }
    }

    @Test
    fun `a calendar day change re-emits`() = runTest {
        saved.save(PlaySlot(alpha, today), byteArrayOf(1))
        completions.setRecords(listOf(record(beta, today)))

        progress().test {
            val before = awaitItem()
            assertEquals(setOf(alpha), before.inProgress)
            assertEquals(setOf(beta), before.solved)

            calendar.setAll(today.plusDays(1))
            // Each game's day rolls over on its own flow; settle on the last emission.
            var after = awaitItem()
            while (after.solved.isNotEmpty() || after.inProgress.isNotEmpty()) after = awaitItem()
            assertFalse(after.anyInProgress)
            assertEquals(emptySet<GameId>(), after.solved)
        }
    }
}

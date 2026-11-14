package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.SolveReplayRef.Latest
import com.ivandsky.puzzlebox.domain.SolveReplayRef.MyLeg
import com.ivandsky.puzzlebox.domain.SolveReplayRef.Record
import com.ivandsky.puzzlebox.domain.SolveReplayRef.TheirLeg
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyReplayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The replay entry: with no replay stored it answers "no replay" for every reference, and latestIsRecord reads the repository. */
class LoadSolveReplayTest {

    private val game = TestGames.alpha
    private val puzzle = TestGames.Ref(game, PuzzleKey.Daily(LocalDate.parse("2026-11-08"), 1))
    private val code = ShareCode.parse("ABC234")!!
    private val refs = listOf(Record(puzzle), Latest(puzzle), MyLeg(code, 0), TheirLeg(code, 0))

    @Test
    fun `with no replay stored it gives null for every reference kind`() = runTest {
        val load = entry()

        refs.forEach { assertNull(load(it)) }
    }

    @Test
    fun `latestIsRecord forwards to the repository`() = runTest {
        assertFalse(entry(twoSolves()).latestIsRecord(puzzle))
    }

    private fun twoSolves() = FakeCompletionRepository(
        listOf(solve(isRecord = true), solve(isRecord = false)),
    )

    private fun solve(isRecord: Boolean) = CompletionRecord(
        puzzleRef = puzzle,
        time = 1_000,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        isRecord = isRecord,
    )

    private fun entry(completions: CompletionRepository = FakeCompletionRepository()): LoadSolveReplay {
        val daily = FakeDailyRepository()
        val received = FakeReceivedPuzzleRepository()
        val loadShared = LoadSharedPuzzle(
            registry = TestGames.registry(listOf(game)),
            shared = FakeSharedPuzzleRepository(),
            received = received,
            budget = { Budget.within(1_000, System::nanoTime) },
            clock = { Instant.EPOCH },
        )
        return LoadSolveReplay(
            games = TestGames.registry(listOf(game)),
            daily = LoadDailyReplay(completions, daily),
            custom = LoadCustomReplay(completions, received, loadShared),
            leg = LoadLegReplay(
                matches = FakeMatchRepository(),
                outbox = FakeMatchResultOutbox(),
                identity = FakeIdentityService(),
                daily = daily,
                received = received,
                loadShared = loadShared,
            ),
            player = LoadPlayerReplay(FakeDailyReplayRepository(), daily),
            completions = completions,
            dispatchers = InlineDispatcherProvider,
        )
    }

    private object InlineDispatcherProvider : DispatcherProvider {
        private val inline = object : CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext) = false
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        }
        override val main: CoroutineDispatcher = inline
        override val io: CoroutineDispatcher = inline
        override val default: CoroutineDispatcher = inline
    }
}

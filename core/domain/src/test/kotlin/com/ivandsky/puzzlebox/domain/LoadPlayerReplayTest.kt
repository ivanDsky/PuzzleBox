package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.replay.DailyReplayRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.FixtureSession
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Another player's replay through the one entry: fetch, cached puzzle, open; absent, denied and corrupt are all null. */
class LoadPlayerReplayTest {

    private val game = ReplayFixtures.GAME
    private val day = LocalDate.parse("2026-11-08")
    private val board = LeaderboardRef(game.value, day)
    private val replay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES).toByteArray()
    private val replays = FakeDailyReplayRepository().apply { putReplay(board, "them", replay) }

    @Test
    fun `a fetched blob opens and ends solved`() = runTest {
        val timeline = checkNotNull(entry()(SolveReplayRef.Player(board, "them")))

        assertEquals(ReplayOutcome.Solved, timeline.outcome)
        assertEquals(ReplayFixtures.SHORT_MOVES * 1_000L, timeline.endMs)
    }

    @Test
    fun `an absent replay gives null`() = runTest {
        assertNull(entry()(SolveReplayRef.Player(board, "nobody")))
    }

    @Test
    fun `a replay the owner's tier denies gives null`() = runTest {
        replays.deniedReads += board to "them"

        assertNull(entry()(SolveReplayRef.Player(board, "them")))
    }

    @Test
    fun `corrupt bytes give null`() = runTest {
        replays.putReplay(board, "them", byteArrayOf(1, 2, 3))

        assertNull(entry()(SolveReplayRef.Player(board, "them")))
    }

    @Test
    fun `a day the cache dropped gives null`() = runTest {
        assertNull(entry(cached = false)(SolveReplayRef.Player(board, "them")))
    }

    @Test
    fun `another kind of reference is not answered`() = runTest {
        val source = LoadPlayerReplay(ThrowingDailyReplayRepository, FakeDailyRepository())(
            SolveReplayRef.Record(TestGames.Ref(game, PuzzleKey.Daily(day, 1))),
        )

        assertNull(source)
    }

    private fun entry(
        cached: Boolean = true,
    ): LoadSolveReplay {
        val daily = FakeDailyRepository().apply { if (cached) setCached(game, day, TestGames.puzzle(game)) }
        val received = FakeReceivedPuzzleRepository()
        val games = GameRegistry(listOf(fixturePlugin()))
        val completions = FakeCompletionRepository()
        val loadShared = LoadSharedPuzzle(
            registry = games,
            shared = FakeSharedPuzzleRepository(),
            received = received,
            budget = { Budget.within(1_000, System::nanoTime) },
            clock = { Instant.EPOCH },
        )
        return LoadSolveReplay(
            games = games,
            daily = LoadDailyReplay(completions, daily),
            custom = LoadCustomReplay(completions, received, loadShared),
            leg = LoadLegReplay(FakeMatchRepository(), FakeMatchResultOutbox(), FakeIdentityService(), daily, received, loadShared),
            player = LoadPlayerReplay(replays, daily),
            completions = completions,
            dispatchers = InlineDispatcherProvider,
        )
    }

    private fun fixturePlugin(): GamePlugin<Unit, FixtureSession, TestGames.Model> {
        val base = TestGames.plugin(game)
        return GamePlugin(
            definition = base.definition,
            rules = base.rules,
            session = fixtureCodec,
            score = fixtureScore,
            stateOf = { },
            initialSession = { _: Puzzle -> DecodeResult.Ok(ReplayFixtures.initial(ReplayFixtures.SHORT_MOVES)) },
            refOf = base.refOf,
            playAgainSession = base.playAgainSession,
            rendererModel = { _, _, _ -> TestGames.Model },
            replay = ReplayFixtures.replay,
        )
    }

    private val fixtureCodec = object : GameSessionCodec<FixtureSession> {
        override fun serialize(session: FixtureSession): ByteArray = ByteArray(0)
        override fun deserialize(bytes: ByteArray): DecodeResult<FixtureSession> =
            DecodeResult.Ok(ReplayFixtures.initial(ReplayFixtures.SHORT_MOVES))
    }

    private val fixtureScore = object : ScoreModel<FixtureSession> {
        override fun completion(session: FixtureSession): CompletionRecord? = null
    }

    private object ThrowingDailyReplayRepository : DailyReplayRepository {
        override suspend fun publish(board: LeaderboardRef, blob: ByteArray): Boolean = error("no read")
        override suspend fun fetch(board: LeaderboardRef, uid: String): ByteArray? = error("no read")
        override suspend fun delete(board: LeaderboardRef) = error("no read")
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

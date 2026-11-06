package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.RankOrderCases
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `best(game)` picks by the game's own rank order (ADR-0013): the shared [RankOrderCases]
 * table run against real Room and [FakeCompletionRepository], one game per case so the
 * cases never see each other's rows. Replays never rank in either.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BestRankContractTest {

    private val day = LocalDate.of(2026, 10, 31)
    private val cases = RankOrderCases.all.mapIndexed { i, case -> GameId("case$i") to case }
    private val orders = cases.associate { (game, case) -> game to case.order }

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `room best follows each game's rank order`() = runTest {
        val registry = GameRegistry(orders.map { (game, order) -> plugin(game, order) })
        assertContract(RoomCompletionRepository(db, db.completionDao(), registry))
    }

    @Test
    fun `fake best follows each game's rank order`() = runTest {
        assertContract(FakeCompletionRepository(rankOrders = orders))
    }

    private suspend fun assertContract(repository: CompletionRepository) {
        for ((game, case) in cases) {
            assertNull(case.name, repository.best(game))
            repository.onCompleted(completion(game, 1, case.previous))
            // A replay is a second play of the same puzzle; otherwise another puzzle's first solve.
            repository.onCompleted(completion(game, if (case.replay) 1 else 2, case.play))
            assertEquals(case.name, case.best, repository.best(game))
        }
    }

    private fun completion(game: GameId, puzzleNumber: Int, score: BestScore) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, day, puzzleNumber),
        time = score.timeMs,
        hints = score.hints,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    /** [TestGames.plugin] under [game], ranking by [order]. */
    private fun plugin(game: GameId, order: RankOrder): GamePlugin<*, *, *> {
        val base = TestGames.plugin(game)
        return GamePlugin(
            definition = base.definition,
            rules = base.rules,
            session = base.session,
            score = object : ScoreModel<TestGames.Session> {
                override fun completion(session: TestGames.Session): CompletionRecord? = null
                override val rankOrder: RankOrder = order
            },
            stateOf = base.stateOf,
            initialSession = base.initialSession,
            refOf = base.refOf,
            replaySession = base.replaySession,
            rendererModel = base.rendererModel,
        )
    }
}

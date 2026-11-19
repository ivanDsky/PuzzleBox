package com.ivandsky.puzzlebox.domain.insights.rankhistory

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeRankHistoryStore
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The kept weekly rank of the signed-in player, one game, read from the device only. */
class ObserveRankHistoryTest {

    private val zip = GameId("zip")
    private val tango = GameId("tango")
    private val ann = Identity("u1", "Ann", null)
    private val bo = Identity("u2", "Bo", null)

    private fun week(owner: String, game: GameId, weekStart: String, points: Double = 50.0) =
        RankHistoryStore.Week(owner, game, LocalDate.parse(weekStart), points, friendCount = 2)

    private val kept = listOf(
        week("u1", zip, "2026-11-09", 70.0),
        week("u1", zip, "2026-11-02", 60.0),
        week("u1", tango, "2026-11-09"),
        week("u2", zip, "2026-11-09", 10.0),
    )

    @Test
    fun `it gives the signed-in player's weeks of the one game, oldest first`() = runTest {
        val observe = ObserveRankHistory(FakeIdentityService(initial = ann), FakeRankHistoryStore(kept))

        assertEquals(
            listOf(week("u1", zip, "2026-11-02", 60.0), week("u1", zip, "2026-11-09", 70.0)),
            observe(zip).first(),
        )
    }

    @Test
    fun `signed out it is empty`() = runTest {
        val observe = ObserveRankHistory(FakeIdentityService(), FakeRankHistoryStore(kept))

        assertEquals(emptyList<RankHistoryStore.Week>(), observe(zip).first())
    }

    @Test
    fun `a game with no kept week is empty`() = runTest {
        val observe = ObserveRankHistory(FakeIdentityService(initial = ann), FakeRankHistoryStore(kept.filter { it.game == zip }))

        assertEquals(emptyList<RankHistoryStore.Week>(), observe(tango).first())
    }

    @Test
    fun `a new account reads its own weeks and signing out empties it`() = runTest {
        val identity = FakeIdentityService(initial = ann)
        val seen = mutableListOf<List<RankHistoryStore.Week>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ObserveRankHistory(identity, FakeRankHistoryStore(kept))(zip).toList(seen)
        }
        testScheduler.runCurrent()

        identity.currentIdentity = bo
        testScheduler.runCurrent()
        identity.currentIdentity = null
        testScheduler.runCurrent()

        assertEquals(
            listOf(
                listOf(week("u1", zip, "2026-11-02", 60.0), week("u1", zip, "2026-11-09", 70.0)),
                listOf(week("u2", zip, "2026-11-09", 10.0)),
                emptyList(),
            ),
            seen,
        )
    }
}

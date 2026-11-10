package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The duel screen's read model: the match, the viewer's completions and the viewer's saved plays, as one. */
class ObserveDuelTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)
    private val date = LocalDate.of(2026, 11, 9)
    private val now = Instant.parse("2026-11-09T10:00:00Z")

    private val server = FakeMatchRepository().apply { this.now = this@ObserveDuelTest.now }
    private val identity = FakeIdentityService()
    private val completions = FakeCompletionRepository()
    private val savedPlays = FakeSavedPlayRepository()
    private val registry = TestGames.registry(listOf(TestGames.alpha, TestGames.beta))
    private val observeDuel = ObserveDuel(
        observeMatch = ObserveMatch(server, FakeMatchResultOutbox(), FakeMatchLocalRepository(), identity, registry) { now },
        completions = completions,
        savedPlays = savedPlays,
    )

    private fun viewAs(player: Player?) {
        identity.currentIdentity = player?.let { Identity(it.uid, it.name, null) }
        server.signedInUid = player?.uid
    }

    /** Ann opens a two-puzzle duel (alpha, beta); the viewer is then [viewer]. */
    private suspend fun annChallenges(viewer: Player?) {
        viewAs(ann)
        val legs = listOf(
            MatchLeg(LegRef.Daily(TestGames.alpha, date, 1), null),
            MatchLeg(LegRef.Daily(TestGames.beta, date, 2), null),
        )
        server.create(MatchDraft(code, 24, ann, null, legs, includeFriends = false, rematchOf = null))
        viewAs(viewer)
    }

    private fun solved(game: GameId, number: Int) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(date, number)),
        time = 1_000,
        hints = 0,
        stats = RecordStats(1, "{}"),
        isRecord = true,
    )

    @Test
    fun theMatchStateIsCarriedAsObserved() = runTest {
        viewAs(bob)

        observeDuel(code).test {
            assertEquals(MatchState.NotFound, awaitItem().match)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noCompletionOfADuelPuzzleMeansNothingSolvedBefore() = runTest {
        annChallenges(viewer = bob)
        completions.setRecords(listOf(solved(TestGames.alpha, number = 7)))

        observeDuel(code).test {
            assertFalse(awaitItem().solvedBefore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCompletionOfAPuzzleInTheDuelIsSolvedBeforeAndFollowsLaterCompletions() = runTest {
        annChallenges(viewer = bob)

        observeDuel(code).test {
            assertFalse(awaitItem().solvedBefore)
            completions.setRecords(listOf(solved(TestGames.alpha, number = 1)))
            assertTrue(awaitItem().solvedBefore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSignedOutViewerStillSeesWhetherTheirDeviceSolvedAPuzzle() = runTest {
        annChallenges(viewer = null)
        completions.setRecords(listOf(solved(TestGames.beta, number = 2)))

        observeDuel(code).test {
            val duel = awaitItem()
            assertTrue(duel.match is MatchState.SignedOut)
            assertTrue(duel.solvedBefore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aMissingMatchIsNeverSolvedBefore() = runTest {
        viewAs(bob)
        completions.setRecords(listOf(solved(TestGames.alpha, number = 1)))

        observeDuel(code).test {
            assertFalse(awaitItem().solvedBefore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun elapsedTimesAreThisDuelsSavedPlaysByPuzzleIndex() = runTest {
        annChallenges(viewer = bob)
        savedPlays.save(PlaySlot.Leg(TestGames.alpha, code.value, 0), byteArrayOf(1), 12_000)
        savedPlays.save(PlaySlot.Leg(TestGames.beta, code.value, 1), byteArrayOf(1), 34_000)
        savedPlays.save(PlaySlot.Leg(TestGames.alpha, "OTHER1", 0), byteArrayOf(1), 56_000)
        savedPlays.save(PlaySlot.Daily(TestGames.alpha, date), byteArrayOf(1), 78_000)

        observeDuel(code).test {
            assertEquals(mapOf(0 to 12_000L, 1 to 34_000L), awaitItem().elapsedMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSavePlayedNowShowsUpWithoutReopening() = runTest {
        annChallenges(viewer = bob)

        observeDuel(code).test {
            assertEquals(emptyMap<Int, Long>(), awaitItem().elapsedMs)
            savedPlays.save(PlaySlot.Leg(TestGames.alpha, code.value, 0), byteArrayOf(1), 5_000)
            assertEquals(mapOf(0 to 5_000L), awaitItem().elapsedMs)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

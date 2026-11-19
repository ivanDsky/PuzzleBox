package com.ivandsky.puzzlebox.play.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.domain.buildSelfMatch
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The leg timer's target: past you's first solve of an open self-duel leg, and nothing else. */
class ObserveLegTargetTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val friendCode = ShareCode.parse("M8T3VQ")!!
    private val self = FakeSelfMatchRepository()
    private val preferences = FakeUserPreferencesRepository()
    private val target = ObserveLegTarget(self, preferences)

    /** Past me solved puzzle 1 in 41 s and puzzle 2 in 52 s. */
    private suspend fun aSelfDuelIsSaved() {
        val legs = List(2) { MatchLeg(LegRef.Daily(TestGames.alpha, LocalDate.of(2026, 11, 1 + it), it + 1), authorUid = null) }
        val records = List(2) {
            CompletionRecord(
                puzzleRef = TestGames.Ref(TestGames.alpha, PuzzleKey.Daily(LocalDate.of(2026, 11, 1 + it), it + 1)),
                time = 41_000L + it * 11_000L,
                hints = 0,
                stats = RecordStats(schemaVersion = 1, blob = "{}"),
                firstCompletedAt = Instant.ofEpochMilli(1_000),
                isRecord = true,
            )
        }
        val draft = buildSelfMatch(code, Instant.ofEpochMilli(5_000_000), includeFriends = false, legs = legs, records = records)
        self.save(draft.match, draft.past)
    }

    @Test
    fun `an open self duel leg targets past you's first solve of that puzzle`() = runTest {
        aSelfDuelIsSaved()
        target(code, 1).test {
            assertEquals(52.seconds, awaitItem())
        }
    }

    @Test
    fun `a friend duel leg has no target`() = runTest {
        aSelfDuelIsSaved()
        target(friendCode, 0).test {
            assertEquals(null, awaitItem())
        }
    }

    @Test
    fun `a leg past the duel's puzzles has no target`() = runTest {
        aSelfDuelIsSaved()
        target(code, 2).test {
            assertEquals(null, awaitItem())
        }
    }

    @Test
    fun `a leg of mine that is already posted has no target`() = runTest {
        aSelfDuelIsSaved()
        target(code, 0).test {
            assertEquals(41.seconds, awaitItem())
            self.post(code, LegResult(SelfSeats.ME, 0, 30_000, 0, 9_000, gaveUp = false))
            assertEquals(null, awaitItem())
        }
    }

    @Test
    fun `switching the setting off removes the target and on brings it back`() = runTest {
        aSelfDuelIsSaved()
        target(code, 0).test {
            assertEquals(41.seconds, awaitItem())
            preferences.setShowSelfDuelTimeLeft(false)
            assertEquals(null, awaitItem())
            preferences.setShowSelfDuelTimeLeft(true)
            assertEquals(41.seconds, awaitItem())
        }
    }
}

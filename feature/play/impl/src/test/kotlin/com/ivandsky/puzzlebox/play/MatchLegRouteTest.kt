package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.createComposeRule
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The duel puzzle route's two decisions: where this puzzle sits in its duel (the caption's
 * opponent, number and count), and whether my own queued result makes it taken, so the route
 * lands on that result instead of a board (A22), never opening a taken puzzle's play while the
 * player is still being restored.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MatchLegRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val code = checkNotNull(ShareCode.parse("M8T3VQ"))
    private val ann = Player("ann-uid", "Ann", photoUrl = null)
    private val ben = Player("ben-uid", "Ben", photoUrl = null)
    private val game = TestGames.alpha
    private val legs = (1..3).map { MatchLeg(LegRef.Daily(game, LocalDate.parse("2026-11-0$it"), it), authorUid = null) }
    private val matches = FakeMatchRepository()
    private val outbox = FakeMatchResultOutbox()

    private fun leg(index: Int) = MatchLegKey(code.value, index, game.value)

    /** Ann's three-puzzle duel as the repository serves it, with [seated] in the other seat. */
    private suspend fun duel(seated: Player? = ben): MatchFetch {
        matches.signedInUid = ann.uid
        matches.create(MatchDraft(code, 24, ann, invitedUid = null, legs, includeFriends = false, rematchOf = null))
        if (seated != null) {
            matches.signedInUid = seated.uid
            matches.claim(code, seated)
        }
        return matches.observe(code).first()
    }

    /** The outbox's rows of the duel after queueing [results]. */
    private suspend fun rows(vararg results: LegResult): List<OutboxResult> {
        results.forEach { outbox.enqueue(code, it) }
        return outbox.observe(code).first()
    }

    private fun solved(player: Player, legIndex: Int, timeMs: Long, hints: Int) =
        LegResult(player.uid, legIndex, timeMs, hints, completedAtMs = 1_000L, gaveUp = false)

    private fun gaveUp(player: Player, legIndex: Int) =
        LegResult(player.uid, legIndex, timeMs = 0L, hints = 0, completedAtMs = 1_000L, gaveUp = true)

    @Test
    fun aFoundMatchGivesTheOpponentTheNumberAndTheCount() = runTest {
        val fetch = duel()

        assertEquals("the challenger plays against the opponent", LegPosition("Ben", 2, 3), legPosition(fetch, leg(1), ann.uid))
        assertEquals("the opponent plays against the challenger", LegPosition("Ann", 3, 3), legPosition(fetch, leg(2), ben.uid))
    }

    @Test
    fun aLegIndexPastTheEndGivesNoPosition() = runTest {
        assertNull(legPosition(duel(), leg(3), ann.uid))

        assertNull(legPosition(matches.observe(checkNotNull(ShareCode.parse("K7Q2MX"))).first(), leg(0), ann.uid))
        matches.online = false
        assertNull(legPosition(matches.observe(code).first(), leg(0), ann.uid))
        assertNull(legPosition(MatchFetch.UnsupportedVersion(found = 2), leg(0), ann.uid))
    }

    @Test
    fun anUnclaimedMatchHasNoOpponentName() = runTest {
        assertEquals(LegPosition(null, 1, 3), legPosition(duel(seated = null), leg(0), ann.uid))
    }

    @Test
    fun aSolvedRowOfMineBecomesItsResultKey() = runTest {
        val rows = rows(solved(ann, legIndex = 0, timeMs = 5_000L, hints = 0), solved(ann, legIndex = 1, timeMs = 61_000L, hints = 2))

        assertEquals(MatchLegResultKey(code.value, 1, 61_000L, 2, gaveUp = false), takenResultKey(rows, leg(1), ann.uid))
    }

    @Test
    fun aGaveUpRowOfMineBecomesAGaveUpResultKey() = runTest {
        val rows = rows(gaveUp(ann, legIndex = 2))

        assertEquals(MatchLegResultKey(code.value, 2, 0L, 0, gaveUp = true), takenResultKey(rows, leg(2), ann.uid))
    }

    @Test
    fun noRowOrOnlyTheOtherPlayersRowOpensTheBoard() = runTest {
        assertNull("no row", takenResultKey(rows(), leg(1), ann.uid))

        val rows = rows(solved(ben, legIndex = 1, timeMs = 9_000L, hints = 1), solved(ann, legIndex = 0, timeMs = 5_000L, hints = 0))
        assertNull("the other player's row of this puzzle", takenResultKey(rows, leg(1), ann.uid))
        assertNull("signed out, nothing is mine", takenResultKey(rows, leg(0), uid = null))
    }

    @Test
    fun aTakenPuzzleWaitsForTheRestoredPlayerAndNeverOpensItsPlay() {
        runBlocking { outbox.enqueue(code, gaveUp(ann, legIndex = 1)) }
        val identity = FakeIdentityService(initial = null)
        val store = startRoute(identity)
        val taken = mutableListOf<MatchLegResultKey>()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                GameRoute(leg(1), PlayLayout.SinglePane, onBack = {}, onSolved = { _, _ -> }, onGaveUp = {}, onTaken = { taken += it })
            }
        }
        composeRule.waitForIdle()
        assertNull("no play while the player is unknown", store.peek(PlaySlot.Leg(game, code.value, 1)))

        identity.currentIdentity = Identity(ann.uid, ann.name, photoUrl = null)
        composeRule.waitForIdle()

        assertEquals(listOf(MatchLegResultKey(code.value, 1, timeMs = 0L, hintsUsed = 0, gaveUp = true)), taken)
        assertNull("a taken puzzle never opens its play", store.peek(PlaySlot.Leg(game, code.value, 1)))
    }

    /** Koin for the route over stand-in games, this test's outbox and [identity]; returns the live store. */
    private fun startRoute(identity: FakeIdentityService): PlaySessionStore {
        val fakes = FakeGames(listOf(game))
        return startKoin {
            modules(
                commonModule,
                module {
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<DailyRepository> { FakeDailyRepository() }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<GameCalendar> { FakeGameCalendar(LocalDate.parse("2026-11-01")) }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single<MatchRepository> { matches }
                    single<MatchResultOutbox> { outbox }
                    single<IdentityService> { identity }
                    factory { FinishLeg(get(), get()) { Instant.EPOCH } }
                    factory { LoadPastDay(get(), get()) }
                },
                playModule,
            )
        }.koin.get()
    }

    @After
    fun tearDown() {
        stopKoin()
    }
}

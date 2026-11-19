package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * One account on two devices (#1651): Ann plays a duel leg on the phone, the tablet shares the server's match and
 * nothing else. Opening the compare page for that leg on the tablet must show her replay.
 */
class ReplayCompareAcrossDevicesTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val gameId = ReplayFixtures.GAME
    private val day = LocalDate.parse("2026-11-04")
    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)
    private val games = replayCapableGames()

    private val server = FakeMatchRepository().apply { signedInUid = ann.uid }
    private val daily = FakeDailyRepository().apply { setCached(gameId, day, TestGames.puzzle(gameId)) }

    private val phoneOutbox = FakeMatchResultOutbox()
    private val tabletOutbox = FakeMatchResultOutbox()

    private fun aDuelOnTheServer() = runBlocking {
        val leg = MatchLeg(LegRef.Daily(gameId, day, 7), authorUid = null)
        server.create(MatchDraft(code, 24, ann, invitedUid = null, listOf(leg), includeFriends = false, rematchOf = null))
        server.signedInUid = bob.uid
        server.claim(code, bob)
        server.signedInUid = ann.uid
    }

    /** Ann solves leg 0 on the phone: queued in its outbox, then posted the way the poster does. */
    private fun annPlaysOnThePhone(replay: SolveReplay?) = runBlocking {
        val result = LegResult(ann.uid, 0, 12_000, hints = 0, completedAtMs = 1_000, gaveUp = false, replay = replay)
        phoneOutbox.enqueue(code, result)
        server.post(code, result)
        phoneOutbox.update(code, 0, ann.uid, ResultPostState.Posted, attempts = 1)
    }

    private fun compareOn(outbox: FakeMatchResultOutbox): ReplayCompareState {
        val key = ReplayCompareKey.of(
            mine = ReplayKey.leg(gameId.value, code.value, 0),
            theirs = ReplayKey.leg(gameId.value, code.value, 0, theirs = true),
            myName = "You",
            theirName = "Bob",
        )
        val ui = FakePlayUi(gameId).apply { replayBoard = ReplayGoldenBoard }
        return ReplayCompareViewModel(
            key = key,
            loadReplay = loadSolveReplay(
                games,
                daily,
                FakeCompletionRepository(),
                matches = server,
                outbox = outbox,
                identity = FakeIdentityService(Identity(ann.uid, ann.name, null)),
            ),
            games = games,
            ui = GamePlayRegistry(listOf(ui)),
            calendar = FakeGameCalendar(day),
            preferences = FakeUserPreferencesRepository(),
        ).state.value
    }

    @Test
    fun `the tablet opens the replay of a leg played on the phone`() {
        aDuelOnTheServer()
        annPlaysOnThePhone(ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES))

        val onTablet = compareOn(tabletOutbox) as ReplayCompareState.Ready

        assertNotNull(onTablet.mine)
        assertEquals("the tablet's replay is the phone's", (compareOn(phoneOutbox) as ReplayCompareState.Ready).mine?.outcome, onTablet.mine?.outcome)
    }

    @Test
    fun `a leg played on the phone with no replay on the server shows no replay on the tablet`() {
        aDuelOnTheServer()
        annPlaysOnThePhone(replay = null)

        assertEquals(ReplayCompareState.None, compareOn(tabletOutbox))
    }

    @Test
    fun `a leg played on this device opens from its own outbox before the server has the result`() {
        aDuelOnTheServer()
        val result = LegResult(ann.uid, 0, 12_000, hints = 0, completedAtMs = 1_000, gaveUp = false, replay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES))
        runBlocking { phoneOutbox.enqueue(code, result) }

        assertNotNull((compareOn(phoneOutbox) as ReplayCompareState.Ready).mine)
    }
}

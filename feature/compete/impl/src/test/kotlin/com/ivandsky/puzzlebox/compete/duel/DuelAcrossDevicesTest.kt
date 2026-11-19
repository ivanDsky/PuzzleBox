package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One account on two devices: phone and tablet share the server's match and nothing else (each has its own outbox,
 * local cache and self store). Ann's "Best of 4" with Bob is played on the phone; the tablet, which never played a
 * puzzle of it, must show what the phone shows (#1541).
 */
class DuelAcrossDevicesTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)
    private val now = Instant.ofEpochSecond(60)

    /** The server both devices talk to; Ann is signed in on it, as on both devices. */
    private val server = FakeMatchRepository().apply { signedInUid = ann.uid }
    private val registry = TestGames.registry(listOf(TestGames.alpha))

    /** A device of Ann's: everything the app keeps on the device itself, over the one [server]. */
    private inner class Device {
        val outbox = FakeMatchResultOutbox()
        val local = FakeMatchLocalRepository()
        val self = FakeSelfMatchRepository()
        val identity = FakeIdentityService(Identity(ann.uid, ann.name, null))

        /** The player solves puzzle [leg] here: queued in this device's outbox, then posted the way the poster does. */
        suspend fun plays(leg: Int, timeMs: Long) {
            val result = LegResult(ann.uid, leg, timeMs, hints = 0, completedAtMs = now.toEpochMilli(), gaveUp = false)
            outbox.enqueue(code, result)
            server.post(code, result)
            outbox.update(code, leg, ann.uid, ResultPostState.Posted, attempts = 1)
        }

        suspend fun view(): MatchView =
            (ObserveMatch(server, outbox, local, identity, registry) { now }(code).first() as MatchState.Loaded).view

        suspend fun row(): DuelRow = ObserveMyDuels(server, self, outbox, local, identity) { now }().first().rows.single()

        suspend fun screen() = duelLegsModel(
            view(),
            elapsedMs = emptyMap(),
            now = now,
            nameOf = { it.value },
            rankOrderOf = { RankOrder.TimeThenHints },
        )
    }

    private val phone = Device()
    private val tablet = Device()

    private suspend fun aDuelOfFourWhereBobPlayedEveryPuzzle() {
        val legs = List(LEGS) { MatchLeg(LegRef.Daily(TestGames.alpha, LocalDate.of(2026, 11, 1 + it), it + 1), authorUid = null) }
        server.create(MatchDraft(code, 24, ann, invitedUid = null, legs, includeFriends = false, rematchOf = null))
        server.signedInUid = bob.uid
        server.claim(code, bob)
        repeat(LEGS) { server.post(code, LegResult(bob.uid, it, 50_000L + it, 0, completedAtMs = 1_000, gaveUp = false)) }
        server.signedInUid = ann.uid
    }

    @Test
    fun theTabletShowsEveryPuzzleThePhonePlayedAsPlayedWithTheSameDuelState() = runTest {
        aDuelOfFourWhereBobPlayedEveryPuzzle()
        repeat(LEGS) { phone.plays(it, timeMs = 60_000L + it) }

        val onPhone = phone.screen()
        val onTablet = tablet.screen()

        assertEquals(List(LEGS) { LegStatus.Solved(60_000L + it, 0) }, onPhone.rows.map { it.status })
        assertEquals("the tablet's puzzles match the phone's", onPhone.rows, onTablet.rows)
        assertTrue("no Start or Resume on a puzzle played elsewhere", onTablet.rows.all { it.action == null })
        assertEquals(DuelFooter.RevealReady, onTablet.footer)
        assertEquals("the duel's result matches the phone's", phone.view().outcome, tablet.view().outcome)
        assertEquals("the Duels list row matches the phone's", phone.row(), tablet.row())
        assertEquals(DuelRowKind.RevealReady, tablet.row().kind)
        assertEquals(LEGS, tablet.row().legsDone)
    }

    @Test
    fun theTabletOffersOnlyThePuzzlesNoDeviceHasPlayed() = runTest {
        aDuelOfFourWhereBobPlayedEveryPuzzle()
        phone.plays(0, timeMs = 60_000)
        phone.plays(1, timeMs = 61_000)

        val onTablet = tablet.screen()

        assertEquals(
            listOf(LegStatus.Solved(60_000, 0), LegStatus.Solved(61_000, 0), LegStatus.New, LegStatus.New),
            onTablet.rows.map { it.status },
        )
        assertEquals(listOf(null, null, LegAction.Start, LegAction.Start), onTablet.rows.map { it.action })
        assertEquals(DuelFooter.Playing, onTablet.footer)
        assertEquals(DuelRowKind.YourMove, tablet.row().kind)
        assertEquals(2, tablet.row().legsDone)
        assertFalse(onTablet.rows.any { it.posting != null })
    }

    private companion object {
        const val LEGS = 4
    }
}

package com.ivandsky.puzzlebox.app.play

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntries
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayController
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayUi
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * ADR-0004 reach across the seam: a Tango win written by the play feature's store
 * survives the full leaderboard derivation — PuzzleRefKeys knows the tango ref, the board
 * contract accepts the key, and the record (time = active-play window) still qualifies.
 */
class TangoCompeteLoopTest {

    private var fakeNow: Instant = Instant.parse("2026-10-31T00:00:00Z")

    /** The real Tango play UI with the controller's clock pinned to [fakeNow]. */
    private val tangoUi = object : GamePlayUi by TangoPlayUi() {
        override fun createController(env: PlayEnvironment): GamePlayController =
            TangoPlayController(env, now = { fakeNow })
    }

    @Test
    fun `a tango record survives the full leaderboard derivation (compete loop)`() = runTest {
        val completions = FakeCompletionRepository()
        val daily = FakeDailyRepository().script(
            TangoDefinition.id,
            DailyToday.Fresh(TangoPlayFixture.envelope()),
            listOf(TangoPlayFixture.entry(TangoDefinition.cadence.today())),
        )
        val store = PlaySessionStore(
            games = GameRegistry(listOf(tangoPlugin)),
            ui = GamePlayRegistry(listOf(tangoUi)),
            daily = daily,
            completions = completions,
            scopeFactory = { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) },
        )
        val play = store.start(TangoDefinition.id) {}!!
        play.load()
        val s = play.controller as TangoPlayController

        fakeNow = fakeNow.plusMillis(90_000)
        val (first, firstValue) = TangoPlayFixture.placements.first()
        s.onPlacement(TangoPlacement(first, firstValue)) // window opens at T+90s
        fakeNow = fakeNow.plusMillis(45_000)
        for ((idx, value) in TangoPlayFixture.placements.drop(1)) {
            s.onPlacement(TangoPlacement(idx, value)) // completes at T+135s
        }

        val record = completions.observeRecords().first()[0]

        // The A4 stamp (isRecord + firstCompletedAt) is Room's onCompleted job; the fake
        // skips it, so emulate the stamp from the session's completedAt exactly as the
        // store derives it.
        assertEquals(45_000L, record.time)
        val stamped = record.copy(isRecord = true, firstCompletedAt = s.session?.completedAt)
        assertNotNull(LeaderboardEntries.fromRecord(stamped, uid = "uid-1", displayName = "Ivan"))
    }
}

package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C1 regression (fix-round-1): the in-composition saveable OUTLIVES the flow — after
 * process death the Koin single is a fresh [PlayFlow] with NO state while the saveable
 * envelope survives. Restore must restart the SAVED game's flow and adopt there, never
 * no-op on a null state and drop the bytes. Also pins the envelope contract: the
 * flow's session-changed callback emits SessionBlob-wrapped bytes (game-named), the
 * one format both restore sources (saveable, history replay) share.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayFlowRestoreTest {

    private val day = LocalDate.parse("2026-10-31")

    private fun flow() = PlayFlow(
        zips = FakeZipDailyRepository(PlayTestSupport.envelope().let { com.ivandsky.puzzlebox.data.repository.ZipToday.Fresh(it) }),
        tangos = FakeTangoDailyRepository(
            todayResult = com.ivandsky.puzzlebox.data.repository.TangoToday.Fresh(TangoPlayFixture.envelope()),
        ),
        completions = FakeCompletionRepository(),
        tangoToday = { day },
    )

    private fun zipEnvelope(path: List<Int> = emptyList()): ByteArray {
        val session = ZipSession(
            ref = ZipPuzzleRef(day, 592),
            puzzle = PlayTestSupport.puzzle(),
            path = path.map { com.ivandsky.puzzlebox.games.zip.Cell(it) },
        )
        return SessionBlob.encode(
            com.ivandsky.puzzlebox.games.zip.ZipDefinition.id,
            ZipSessionCodec.serialize(session),
        )
    }

    @Test
    fun `a dead flow is restarted for the saved game and adopts the session`() = runBlocking {
        // Process death: a brand-new flow instance (Koin single reborn), state null.
        val flow = flow()
        assertNull(flow.state)

        var persisted: ByteArray? = null
        val restored = flow.restore(SessionBlob.decode(zipEnvelope(listOf(0)))!!) { persisted = it }

        assertTrue("expected the zip state back", restored is ZipPlayState)
        assertEquals(listOf(0), (flow.state as ZipPlayState).session?.path?.map { it.idx })
    }

    @Test
    fun `a live same-game flow is adopted in place without a restart`() = runBlocking {
        val flow = flow()
        val live = flow.start(PlayGame.Zip) {}
        // Rotation: the flow survived; the saveable re-adopted into the SAME instance.
        val restored = flow.restore(SessionBlob.decode(zipEnvelope(listOf(3)))!!) {}
        assertSame(live, restored)
        assertEquals(listOf(3), (flow.state as ZipPlayState).session?.path?.map { it.idx })
    }

    @Test
    fun `a stale other-game flow is released before the saved game starts`() = runBlocking {
        val flow = flow()
        flow.start(PlayGame.Tango) {}

        flow.restore(SessionBlob.decode(zipEnvelope(listOf(1)))!!) {}

        assertTrue("expected the zip state after the tango flow was released", flow.state is ZipPlayState)
        assertEquals(listOf(1), (flow.state as ZipPlayState).session?.path?.map { it.idx })
    }

    @Test
    fun `an unknown game id leaves the flow untouched`() = runBlocking {
        val flow = flow()
        val live = flow.start(PlayGame.Zip) {}

        val restored = flow.restore(SessionBlob.Decoded(game = com.ivandsky.puzzlebox.model.GameId("not-a-game"), session = ByteArray(0))) {}

        assertNull(restored)
        assertSame(live, flow.state)
    }

    @Test
    fun `the session-changed callback emits game-named envelope bytes`() = runBlocking {
        val flow = flow()
        var persisted: ByteArray? = null
        flow.start(PlayGame.Tango) { persisted = it }

        (flow.state as TangoPlayState).load()
        val session = (flow.state as TangoPlayState).session
        assertEquals(TangoPuzzleRef(day, null), session?.ref)

        // The route's saveable carries the envelope, not bare codec bytes — this is
        // what lets a process-dead flow recover WHICH game to restart (C1).
        val decoded = persisted?.let(SessionBlob::decode)
        assertTrue("expected an envelope", decoded != null)
        assertEquals(com.ivandsky.puzzlebox.games.tango.TangoDefinition.id, decoded!!.game)
        assertTrue(TangoSessionCodec.deserialize(decoded.session) is com.ivandsky.puzzlebox.game.DecodeResult.Ok)
    }
}

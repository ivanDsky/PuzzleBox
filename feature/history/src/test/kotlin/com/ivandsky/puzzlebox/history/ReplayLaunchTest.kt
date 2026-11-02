package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The replay hand-off (ticket 14 → 13; tango-05 generalizes it): the blob must
 * round-trip through the NAMED game's own session codec with THIS day's identity
 * (ADR-0005 envelope), and untrustable inputs never launch (A16).
 */
class ReplayLaunchTest {

    private val day: LocalDate = LocalDate.of(2026, 10, 31)

    @Test
    fun `missing pieces launch nothing`() = runTest {
        assertEquals(
            ReplayLaunch.Missing,
            replayLaunch(ZipDefinition.id, day, cached = null, puzzleNumber = 592),
        )
        assertEquals(
            ReplayLaunch.Missing,
            replayLaunch(TangoDefinition.id, day, cached = null, puzzleNumber = 301),
        )
        assertEquals(
            ReplayLaunch.Missing,
            replayLaunch(
                com.ivandsky.puzzlebox.model.GameId("queens"),
                day,
                cached = HistoryTestSupport.envelope(),
                puzzleNumber = 1,
            ),
        )
    }

    @Test
    fun `zip blob round-trips through the play session codec`() = runTest {
        val original = HistoryTestSupport.puzzle()
        val launch = replayLaunch(
            ZipDefinition.id,
            day,
            cached = HistoryTestSupport.envelope(original),
            puzzleNumber = 592,
        )

        // The envelope NAMES the game (ADR-0005) — the play route dispatches on it.
        val blob = SessionBlob.decode((launch as ReplayLaunch.Ready).session)
        assertEquals(ZipDefinition.id, blob?.game)

        val restored = ZipSessionCodec.deserialize(blob!!.session)
        assertTrue("codec must accept the blob", restored is DecodeResult.Ok)
        val session = (restored as DecodeResult.Ok).value
        // The ref is the replayed DAY's identity, never today's (ADR-0004/A4 attribution).
        assertEquals(ZipPuzzleRef(day, 592), session.ref)
        assertEquals(original, session.puzzle)
        assertTrue(!session.isComplete)
        assertTrue(session.path.isEmpty())
    }

    @Test
    fun `tango blob round-trips through its session codec`() = runTest {
        val original = HistoryTestSupport.tangoPuzzle
        val launch = replayLaunch(
            TangoDefinition.id,
            day,
            cached = HistoryTestSupport.tangoEnvelope(original),
            puzzleNumber = 301,
        )

        val blob = SessionBlob.decode((launch as ReplayLaunch.Ready).session)
        assertEquals(TangoDefinition.id, blob?.game)

        val restored = TangoSessionCodec.deserialize(blob!!.session)
        assertTrue("codec must accept the blob", restored is DecodeResult.Ok)
        val session = (restored as DecodeResult.Ok).value as TangoSession
        assertEquals(TangoPuzzleRef(day, 301), session.ref)
        assertEquals(original, session.puzzle)
        assertArrayEquals(original.markers.toTypedArray(), session.puzzle.markers.toTypedArray())
    }

    @Test
    fun `missing puzzle number still yields a valid ref`() = runTest {
        val zip = SessionBlob.decode(
            (replayLaunch(ZipDefinition.id, day, HistoryTestSupport.envelope(), null) as ReplayLaunch.Ready).session,
        )
        val zipSession = ZipSessionCodec.deserialize(zip!!.session)
        assertEquals(ZipPuzzleRef(day, null), (zipSession as DecodeResult.Ok).value.ref)

        val tango = SessionBlob.decode(
            (replayLaunch(TangoDefinition.id, day, HistoryTestSupport.tangoEnvelope(), null) as ReplayLaunch.Ready).session,
        )
        val tangoSession = TangoSessionCodec.deserialize(tango!!.session)
        assertEquals(TangoPuzzleRef(day, null), (tangoSession as DecodeResult.Ok).value.ref)
    }

    @Test
    fun `unreadable zip payload never launches (A16)`() = runTest {
        val corrupt = Puzzle(
            gameId = ZipDefinition.id,
            variant = HistoryTestSupport.puzzle().variant,
            formatVersion = ZipCodec.VERSION,
            payload = "this is not a zip".toByteArray(Charsets.UTF_8),
        )
        val launch = replayLaunch(ZipDefinition.id, day, corrupt, 592)
        // The version byte is the ASCII 't' the engine never shipped — the explicit
        // unsupported-version branch, not a guessed decode (ADR-0005).
        assertEquals(
            ReplayLaunch.Unreadable(DecodeError.UnsupportedVersion(found = 't'.code, maxKnown = ZipCodec.VERSION)),
            launch,
        )
    }

    @Test
    fun `ambiguous tango archive is refused by the A15 gate (A16)`() = runTest {
        val launch = replayLaunch(
            TangoDefinition.id,
            day,
            HistoryTestSupport.tangoEnvelope(HistoryTestSupport.ambiguousTangoPuzzle),
            301,
        )

        val unreadable = launch as ReplayLaunch.Unreadable
        assertEquals(
            DecodeError.Malformed("archived puzzle is not uniquely solvable"),
            unreadable.error,
        )
    }
}

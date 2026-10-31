package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.data.repository.ZipDailyEntry
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The replay hand-off (ticket 14 → 13): the blob must round-trip through the play
 * screen's own session codec with THIS day's identity, and untrustable inputs never
 * launch (A16).
 */
class ReplayLaunchTest {

    private val day: LocalDate = LocalDate.of(2026, 10, 31)

    private fun entry(day: LocalDate, puzzleNumber: Int?): ZipDailyEntry =
        HistoryTestSupport.entry(day, puzzleNumber)

    @Test
    fun `missing pieces launch nothing`() = runTest {
        assertEquals(ReplayLaunch.Missing, replayLaunch(cached = null, entry = entry(day, 592)))
        assertEquals(
            ReplayLaunch.Missing,
            replayLaunch(cached = HistoryTestSupport.envelope(), entry = null),
        )
    }

    @Test
    fun `ready blob round-trips through the play session codec`() = runTest {
        val original = HistoryTestSupport.puzzle()
        val launch = replayLaunch(
            cached = HistoryTestSupport.envelope(original),
            entry = entry(day, puzzleNumber = 592),
        )

        val ready = launch as ReplayLaunch.Ready
        val restored = ZipSessionCodec.deserialize(ready.session)
        assertTrue("codec must accept the blob", restored is DecodeResult.Ok)
        val session = (restored as DecodeResult.Ok).value
        // The ref is the replayed DAY's identity, never today's (ADR-0004/A4 attribution).
        assertEquals(ZipPuzzleRef(day, 592), session.ref)
        assertEquals(original, session.puzzle)
        assertTrue(!session.isComplete)
        assertTrue(session.path.isEmpty())
    }

    @Test
    fun `missing puzzle number still yields a valid ref`() = runTest {
        val launch = replayLaunch(
            cached = HistoryTestSupport.envelope(),
            entry = entry(day, puzzleNumber = null),
        )
        val session = ZipSessionCodec.deserialize((launch as ReplayLaunch.Ready).session)
        assertEquals(ZipPuzzleRef(day, null), (session as DecodeResult.Ok).value.ref)
    }

    @Test
    fun `unreadable payload never launches (A16)`() = runTest {
        val corrupt = Puzzle(
            gameId = ZipDefinition.id,
            variant = HistoryTestSupport.puzzle().variant,
            formatVersion = ZipCodec.VERSION,
            payload = "this is not a zip".toByteArray(Charsets.UTF_8),
        )
        val launch = replayLaunch(cached = corrupt, entry = entry(day, 592))
        // The version byte is the ASCII 't' the engine never shipped — the explicit
        // unsupported-version branch, not a guessed decode (ADR-0005).
        assertEquals(
            ReplayLaunch.Unreadable(DecodeError.UnsupportedVersion(found = 't'.code, maxKnown = ZipCodec.VERSION)),
            launch,
        )
    }
}

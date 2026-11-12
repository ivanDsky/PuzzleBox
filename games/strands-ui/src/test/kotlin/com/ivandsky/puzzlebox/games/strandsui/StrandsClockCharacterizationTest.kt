package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.codec.StrandsSessionCodec
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Strands clock's observable values (displayed time at each point, banked time
 * and the completion record's time) across start, tick, background, foreground and win,
 * so moving its arithmetic onto the shared play-time function cannot shift them.
 */
class StrandsClockCharacterizationTest {

    private var clockMs = 0L
    private val saved = mutableListOf<ByteArray>()
    private val won = mutableListOf<CompletionRecord>()

    private fun controller() = StrandsPlayController(
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, { won += it }),
        now = { Instant.ofEpochMilli(clockMs) },
    )

    /** What the timer bar reads after the loop's next tick. */
    private fun StrandsPlayController.shown(): Long {
        tick()
        return displayedElapsedMs()
    }

    /** Taps [path] letter by letter, then its last letter again to submit. */
    private fun StrandsPlayController.play(path: List<Coord>) {
        path.forEach(::tap)
        tap(path.last())
    }

    private fun lastSaved(): StrandsSession =
        (StrandsSessionCodec.deserialize(saved.last()) as DecodeResult.Ok).value

    @Test
    fun `start tick background foreground and win pin the shown time and the completion time`() {
        clockMs = 1_000
        val c = controller()
        assertEquals(Adopt.Adopted, c.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref))

        assertEquals("start", 0L, c.shown())

        clockMs = 5_000
        assertEquals("tick", 4_000L, c.shown())

        clockMs = 6_000
        c.onBackground()
        assertEquals("background banks the window", 5_000L, lastSaved().elapsedMs)
        assertNull(c.clockWindow)
        assertEquals("background", 5_000L, c.shown())

        clockMs = 20_000 // away: never counted
        assertEquals("away", 5_000L, c.shown())

        clockMs = 21_000
        c.onForeground()
        assertEquals("foreground opens at zero", 5_000L, c.shown())

        clockMs = 22_000
        assertEquals("foreground tick", 6_000L, c.shown())

        c.play(StrandsUiFixtures.row(0))
        clockMs = 25_000
        assertEquals("a found word does not stop the clock", 9_000L, c.shown())

        clockMs = 30_000
        (1 until 4).forEach { c.play(StrandsUiFixtures.row(it)) } // completes at 30 s: 5 s banked + 9 s window
        assertTrue(c.isComplete)
        assertNull(c.clockWindow)
        assertEquals("win banks the window", 14_000L, lastSaved().elapsedMs)
        assertEquals(1, won.size)
        assertEquals("completion record time", 14_000L, won.single().time)

        clockMs = 35_000
        c.onForeground() // a finished play never reopens
        assertNull("a finished play never reopens", c.clockWindow)
        assertEquals("finished play stays frozen", 14_000L, c.shown())
    }
}

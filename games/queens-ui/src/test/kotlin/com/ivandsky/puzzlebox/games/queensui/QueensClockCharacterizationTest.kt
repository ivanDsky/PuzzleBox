package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.CompletionRecord
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
 * Pins the Queens clock's observable values (displayed time at each point, banked time
 * and the completion record's time) across start, tick, background, foreground and win,
 * so moving its arithmetic onto the shared play-time function cannot shift them.
 */
class QueensClockCharacterizationTest {

    private var fakeNow: Instant = Instant.parse("2026-11-02T00:00:00Z")
    private val won = mutableListOf<CompletionRecord>()

    private fun controller() = QueensPlayController(
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { }, { won += it }),
        settings = InMemoryQueensSettings(),
        now = { fakeNow },
        hintMode = QueensPlayFixtures.hintMode,
    )

    private fun advance(ms: Long) {
        fakeNow = fakeNow.plusMillis(ms)
    }

    private fun QueensPlayController.shown(): Long {
        tick()
        return displayedElapsedMs()
    }

    /** The §5 tap cycle: markless → MANUAL X → queen, two taps per solution cell. */
    private fun solve(controller: QueensPlayController) {
        val board = controller.board!!
        for (cell in QueensPlayFixtures.solution) {
            board.onTap(cell.idx)
            board.onTap(cell.idx)
        }
    }

    @Test
    fun `start tick background foreground and win pin the shown time and the completion time`() {
        val s = controller()
        assertEquals(
            Adopt.Adopted,
            s.adoptToday(QueensPlayFixtures.envelope(), QueensPlayFixtures.ref()),
        )

        assertEquals("start", 0L, s.shown())

        advance(4_000)
        assertEquals("tick", 4_000L, s.shown())

        s.onBackground()
        assertEquals("background banks the window", 4_000L, s.board?.session?.elapsedMs)
        assertEquals("background", 4_000L, s.shown())
        assertNull(s.clockWindow)

        advance(30_000) // away: never counted
        assertEquals("away", 4_000L, s.shown())

        s.onForeground()
        advance(1_000)
        assertEquals("foreground tick", 5_000L, s.shown())

        advance(2_000)
        assertEquals("foreground tick 2", 7_000L, s.shown())

        advance(45_000)
        solve(s) // completes at 52 s

        assertTrue(s.isComplete)
        assertEquals("win banks the window", 52_000L, s.board?.session?.elapsedMs)
        assertEquals(1, won.size)
        assertEquals("completion record time", 52_000L, won[0].time)
        assertNull(s.clockWindow)

        advance(9_000)
        s.onForeground() // a finished play never reopens
        assertEquals("finished play stays frozen", 52_000L, s.shown())
    }
}

package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the Zip clock on an injected clock across start, tick, background, foreground and
 * win: the displayed time at each point and the time in the completion record.
 */
class ZipClockCharacterizationTest {

    private val t0: Instant = Instant.ofEpochMilli(1_000_000)

    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds)

    private fun fresh() = ZipSession(
        ref = ZipPuzzleRef(PuzzleKey.Daily(java.time.LocalDate.of(2026, 10, 30), 592)),
        puzzle = ZipTestSupport.n5Puzzle(),
    )

    @Test
    fun `displayed time and completion record follow start tick background foreground win`() {
        // start: the owner opens the window at t=0
        var s = fresh().onForeground(at(0))
        assertEquals(0L, s.displayedElapsedMs(at(0)))
        assertEquals(5_000L, s.displayedElapsedMs(at(5)))

        // tick: a move at t=10 does not change the clock; the window keeps running
        s = s.withMove(ZipMove.Extend(Cell(0)), at(10))
        assertEquals(10_000L, s.displayedElapsedMs(at(10)))
        assertEquals(12_000L, s.displayedElapsedMs(at(12)))

        // background at t=12 banks the window; the gap until foreground never counts
        s = s.onBackground(at(12))
        assertNull(s.runningSince)
        assertEquals(12_000L, s.elapsedMs)
        assertEquals(12_000L, s.displayedElapsedMs(at(60)))

        // foreground at t=60 reopens the window on top of the banked time
        s = s.onForeground(at(60))
        assertEquals(12_000L, s.displayedElapsedMs(at(60)))
        assertEquals(15_000L, s.displayedElapsedMs(at(63)))
        assertEquals(32_000L, s.displayedElapsedMs(at(80)))

        // win: the solution is walked one cell per second from t=64; the last cell wins at t=87
        ZipTestSupport.n5Solution.drop(1).forEachIndexed { i, cell ->
            s = s.withMove(ZipMove.Extend(cell), at(64L + i))
        }
        assertEquals(at(87), s.completedAt)
        assertEquals(39_000L, s.elapsedMs)
        assertNull(s.runningSince)
        assertEquals(39_000L, s.displayedElapsedMs(at(200)))
        assertEquals(39_000L, ZipScoreModel().completion(s)!!.time)
    }
}

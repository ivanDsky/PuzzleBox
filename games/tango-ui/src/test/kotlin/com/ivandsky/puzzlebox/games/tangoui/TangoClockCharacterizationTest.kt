package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Tango clock's observable values (displayed time at each point, banked time
 * and the completion record's time) across start, tick, background, foreground and win,
 * so moving its arithmetic onto the shared play-time function cannot shift them.
 */
class TangoClockCharacterizationTest {

    private val today: LocalDate = LocalDate.parse("2026-10-31")
    private var fakeNow: Instant = Instant.parse("2026-10-31T00:00:00Z")
    private val won = mutableListOf<CompletionRecord>()

    private fun controller() = TangoPlayController(
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { }, { won += it }),
        now = { fakeNow },
        hintMode = HintMode.Stepwise(Dispatchers.Unconfined),
    )

    private fun advance(ms: Long) {
        fakeNow = fakeNow.plusMillis(ms)
    }

    private fun TangoPlayController.shown(): Long {
        tick()
        return displayedElapsedMs()
    }

    @Test
    fun `start tick background foreground and win pin the shown time and the completion time`() {
        val s = controller()
        assertEquals(
            Adopt.Adopted,
            s.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(today, 301)),
        )

        assertEquals("start", 0L, s.shown())

        advance(4_000)
        assertEquals("tick", 4_000L, s.shown())

        s.onBackground()
        assertEquals("background banks the window", 4_000L, s.session?.elapsedMs)
        assertEquals("background", 4_000L, s.shown())
        assertNull(s.clockWindow)

        advance(30_000) // away: never counted
        assertEquals("away", 4_000L, s.shown())

        s.onForeground()
        advance(1_000)
        assertEquals("foreground tick", 5_000L, s.shown())

        advance(2_000)
        assertEquals("foreground tick 2", 7_000L, s.shown())

        val (firstIdx, firstValue) = TangoPlayFixture.placements.first()
        s.onPlacement(TangoPlacement(firstIdx, firstValue))
        assertEquals("placement does not stop the clock", 7_000L, s.shown())

        advance(45_000)
        for ((idx, value) in TangoPlayFixture.placements.drop(1)) {
            s.onPlacement(TangoPlacement(idx, value)) // completes at 52 s
        }

        assertTrue(s.isComplete)
        assertEquals("win banks the window", 52_000L, s.session?.elapsedMs)
        assertEquals(1, won.size)
        assertEquals("completion record time", 52_000L, won[0].time)
        assertNull(s.clockWindow)

        advance(9_000)
        s.onForeground() // a finished play never reopens
        assertEquals("finished play stays frozen", 52_000L, s.shown())
    }
}

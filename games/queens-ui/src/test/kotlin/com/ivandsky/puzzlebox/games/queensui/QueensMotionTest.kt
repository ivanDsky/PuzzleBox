package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Spec A13's satisfaction motion on a paused clock: the host turns each player commit
// into Pop / Pulse / Shake / the solve orchestration, and the board as first seen plays
// nothing. The 4×4 fixture's regions are its rows.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class QueensMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun host(state: QueensBoardState): () -> QueensMotionState {
        var motion: QueensMotionState? = null
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) { motion = rememberQueensMotion(state) }
        }
        frames(2)
        return { checkNotNull(motion) }
    }

    /** A player commit, announced as the app's snapshot manager would before the next frame. */
    private fun commit(block: () -> Unit) = composeRule.runOnIdle {
        block()
        Snapshot.sendApplyNotifications()
    }

    private fun frames(count: Int) = repeat(count) { composeRule.mainClock.advanceTimeByFrame() }

    /** One queen through the tap cycle: a bare cell takes its × first. */
    private fun QueensBoardState.place(idx: Int) {
        if (session.markAt(Cell(idx)) == null) onTap(idx)
        onTap(idx)
    }

    @Test
    fun `a placement pops the queen in and its clean region pulses`() {
        val state = QueensPlayFixtures.boardState(QueensUiFixtures.session())
        val motion = host(state)

        commit { state.place(1) }
        frames(3)
        composeRule.runOnIdle {
            assertNotEquals("mid-pop scale", 1f, motion().scaleOf(1))
            assertTrue("mid-pop alpha ${motion().alphaOf(1)}", motion().alphaOf(1) < 1f)
            assertTrue("region 0 washes", motion().washOf(1) > 0f)
            assertEquals("another region stays still", 0f, motion().washOf(4))
            assertEquals("a clean landing never shakes", 0f, motion().shakeOf(1))
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle {
            assertEquals(1f, motion().scaleOf(1))
            assertEquals(1f, motion().alphaOf(1))
            assertEquals(0f, motion().washOf(1))
        }
    }

    @Test
    fun `a clashing queen shakes with its rival and nothing pulses`() {
        val state = QueensPlayFixtures.boardState(QueensUiFixtures.session())
        val motion = host(state)
        commit { state.place(1) }
        composeRule.mainClock.advanceTimeBy(1_000)

        commit { state.place(2) } // same row = same region
        frames(3)
        composeRule.runOnIdle {
            assertNotEquals("the new queen shakes", 0f, motion().shakeOf(2))
            assertNotEquals("its rival shakes", 0f, motion().shakeOf(1))
            assertEquals("a clash never pulses", 0f, motion().washOf(0))
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(0f, motion().shakeOf(2)) }
    }

    @Test
    fun `the board as first seen and a cross tap play nothing`() {
        var restored = QueensSession(ref = null, puzzle = QueensUiFixtures.puzzle())
        restored = restored.withQueenPlaced(Cell(1), QueensUiFixtures.T0)
        val state = QueensPlayFixtures.boardState(restored)
        val motion = host(state)
        composeRule.runOnIdle {
            assertEquals(1f, motion().scaleOf(1))
            assertEquals(0f, motion().washOf(1))
        }
        commit { state.onTap(15) } // a bare cell takes a ×
        frames(3)
        composeRule.runOnIdle {
            assertEquals(1f, motion().scaleOf(15))
            assertEquals(0f, motion().washOf(15))
            assertEquals(1f, motion().crossOf(15))
        }
    }

    @Test
    fun `a live solve fades the crosses out, then pulses the queens in placement order`() {
        val state = QueensPlayFixtures.boardState(QueensUiFixtures.session())
        val motion = host(state)
        commit { listOf(1, 7, 8).forEach { state.place(it) } }
        composeRule.mainClock.advanceTimeBy(1_000)

        commit { state.place(14) }
        // Past the widest cross fade (the cap) but before the last queen's turn.
        composeRule.mainClock.advanceTimeBy(QueensMotion.CROSS_CAP_MS + QueensMotion.CROSS_FADE_MS + 32L)
        composeRule.runOnIdle {
            assertEquals("every cross has faded", 0f, motion().crossOf(0))
            assertEquals(0f, motion().crossOf(15))
            assertEquals("the last placement does not region-pulse", 0f, motion().washOf(12))
            assertTrue("first queen pulses ${motion().scaleOf(1)}", motion().scaleOf(1) > 1f)
            assertEquals("last queen waits its turn", 1f, motion().scaleOf(14))
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.runOnIdle {
            assertEquals(1f, motion().scaleOf(14))
            assertEquals("crosses stay hidden on the solved board", 0f, motion().crossOf(0))
        }
    }

    @Test
    fun `auto crosses stagger outward by Chebyshev ring`() {
        val n = 8
        val origin = 3 * n + 3
        assertEquals(0L, QueensMotion.staggerDelayMs(origin, origin, n))
        assertEquals(QueensMotion.STAGGER_STEP_MS, QueensMotion.staggerDelayMs(origin + 1, origin, n))
        assertEquals(QueensMotion.STAGGER_STEP_MS, QueensMotion.staggerDelayMs(origin + n + 1, origin, n))
        assertEquals(4 * QueensMotion.STAGGER_STEP_MS, QueensMotion.staggerDelayMs(7 * n + 3, origin, n))
        assertEquals(0L, QueensMotion.staggerDelayMs(5, null, n))
    }

    @Test
    fun `the solve hand-off waits for the orchestration, and not at all under reduced motion`() {
        val lead = QueensMotion.QUEEN_LEAD_MS + 8 * QueensMotion.QUEEN_STEP_MS + QueensMotion.SWEEP_GAP_MS
        assertEquals(lead, QueensMotion.solveLeadMs(queens = 8, reducedMotion = false))
        assertEquals(0L, QueensMotion.solveLeadMs(queens = 8, reducedMotion = true))
    }

    @Test
    fun `queen taps buzz heavy, a cross tap ticks light, a no-op is silent`() {
        val state = QueensPlayFixtures.boardState(QueensUiFixtures.session())
        var before = state.session
        state.onTap(0)
        assertEquals(HapticFeedbackType.TextHandleMove, state.tapHaptic(before))
        before = state.session
        state.onTap(0)
        assertEquals(HapticFeedbackType.LongPress, state.tapHaptic(before))
        before = state.session
        state.onTap(0)
        assertEquals(HapticFeedbackType.LongPress, state.tapHaptic(before))
        before = state.session
        state.onTap(99) // off the board: nothing changed
        assertNull(state.tapHaptic(before))
    }
}

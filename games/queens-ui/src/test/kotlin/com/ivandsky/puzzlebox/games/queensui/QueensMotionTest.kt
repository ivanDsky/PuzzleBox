package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.gameui.LocalSolveRestOnBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    private fun host(state: QueensBoardState, rest: Boolean = false): () -> QueensMotionState {
        var motion: QueensMotionState? = null
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalSolveRestOnBoard provides rest) {
                PuzzleBoxTheme(darkTheme = false) { motion = rememberQueensMotion(state) }
            }
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

    /** A 13×13 board whose regions are its rows, one queen short of solved; returns the last queen's cell. */
    private fun thirteenQueens(rest: Boolean): Triple<QueensBoardState, () -> QueensMotionState, Int> {
        val n = 13
        val cells = List(n) { row -> row * n + (2 * row) % n }
        val puzzle = QueensPuzzle(size = n, regions = List(n * n) { it / n }, solution = cells.map(::Cell))
        val state = QueensPlayFixtures.boardState(QueensSession(ref = null, puzzle = puzzle))
        val motion = host(state, rest)
        commit { cells.dropLast(1).forEach { state.place(it) } }
        composeRule.mainClock.advanceTimeBy(1_000)
        return Triple(state, motion, cells.last())
    }

    @Test
    fun `in the play chrome, thirteen queens step faster so the last pulse is over 1300 ms after the solve`() {
        val (state, motion, last) = thirteenQueens(rest = true)

        commit { state.place(last) }
        // The 70 ms step would start the last queen 200 + 12 × 70 = 1040 ms in; the squeezed step starts it sooner.
        composeRule.mainClock.advanceTimeBy(1_040)
        composeRule.runOnIdle { assertTrue("last queen pulses ${motion().scaleOf(last)}", motion().scaleOf(last) > 1f) }
        composeRule.mainClock.advanceTimeBy(1_300 - 1_040 + 32L)
        composeRule.runOnIdle {
            assertEquals("the last pulse is over", 1f, motion().scaleOf(last))
            assertEquals(0f, motion().washOf(last))
        }
    }

    @Test
    fun `outside the play chrome (a Library lesson), thirteen queens keep the 70 ms step`() {
        val (state, motion, last) = thirteenQueens(rest = false)

        commit { state.place(last) }
        composeRule.mainClock.advanceTimeBy(1_040)
        composeRule.runOnIdle { assertEquals("last queen waits its turn", 1f, motion().scaleOf(last)) }
        composeRule.mainClock.advanceTimeBy(1_300 - 1_040 + 32L)
        composeRule.runOnIdle { assertTrue("last queen still pulses ${motion().scaleOf(last)}", motion().scaleOf(last) > 1f) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(1f, motion().scaleOf(last)) }
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
    fun `in the solve moment the queens' step shortens only when the 70 ms one would run past 1300 ms`() {
        val motionEnd = 1_300L
        for (n in 1..12) assertEquals("$n queens", QueensMotion.QUEEN_STEP_MS, QueensMotion.solveStepMs(n, inMoment = true))
        assertEquals(66L, QueensMotion.solveStepMs(13, inMoment = true))
        assertEquals(57L, QueensMotion.solveStepMs(15, inMoment = true))
        for (n in 1..27) {
            val step = QueensMotion.solveStepMs(n, inMoment = true)
            val lastStart = QueensMotion.QUEEN_LEAD_MS + (n - 1) * step
            assertTrue("$n queens end at ${QueensMotion.solveTotalMs(n, step)} ms", QueensMotion.solveTotalMs(n, step) <= motionEnd)
            assertEquals("$n queens", 1f, QueensMotion.solveQueenScale(motionEnd.toFloat(), n - 1, step))
            assertEquals("$n queens", 0f, QueensMotion.solveQueenWash(motionEnd.toFloat(), n - 1, step))
            assertTrue("$n queens: the last one still pulses", QueensMotion.solveQueenScale(lastStart + 150f, n - 1, step) > 1f)
            assertEquals("$n queens: and waits its turn", 1f, QueensMotion.solveQueenScale(lastStart.toFloat(), n - 1, step))
        }
        assertEquals("the floor wins over the bound", 30L, QueensMotion.solveStepMs(40, inMoment = true))
    }

    @Test
    fun `outside the solve moment the queens keep the 70 ms step whatever the board`() {
        for (n in listOf(1, 4, 8, 13, 40)) assertEquals("$n queens", QueensMotion.QUEEN_STEP_MS, QueensMotion.solveStepMs(n, inMoment = false))
        assertEquals(200L + 12 * 70 + 300, QueensMotion.solveTotalMs(13, QueensMotion.QUEEN_STEP_MS))
    }
}

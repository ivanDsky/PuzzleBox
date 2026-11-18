package com.ivandsky.puzzlebox.games.sudokuui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.LocalSolveRestOnBoard
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val peanut450 = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))
    private val openCells = givens.indices.filter { givens[it] == 0 }
    private val haptics = RecordingHaptics()

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private fun SudokuBoardState.place(idx: Int, digit: Int = solution[idx]) {
        select(idx)
        input(digit)
    }

    private fun setGrid(state: SudokuBoardState): () -> SudokuMotionState {
        var motion: SudokuMotionState? = null
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    val live = rememberSudokuMotion(state)
                    motion = live
                    CompositionLocalProvider(LocalSudokuMotion provides live) {
                        SudokuGrid(state.model, state::select)
                    }
                }
            }
        }
        return { motion!! }
    }

    private fun frames(count: Int) = repeat(count) { composeRule.mainClock.advanceTimeByFrame() }

    //region paused clock

    @Test
    fun `a place pops in from 0_90 with a tick, a restored digit does not`() {
        val restored = SudokuBoardState(SudokuSession(ref = ref, puzzle = peanut450)).apply { place(0) }.session
        val state = SudokuBoardState(restored)
        val motion = setGrid(state)
        frames(3)
        composeRule.runOnIdle {
            assertEquals(1f, motion().scaleOf(0))
            assertEquals(emptyList<HapticFeedbackType>(), haptics.played)
        }

        composeRule.runOnIdle { state.place(1) }
        frames(2)
        composeRule.runOnIdle {
            val scale = motion().scaleOf(1)
            assertTrue("mid-pop scale $scale", scale >= SudokuMotion.POP_FROM && scale < 1f)
            assertEquals(listOf(HapticFeedbackType.TextHandleMove), haptics.played)
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(1f, motion().scaleOf(1)) }
    }

    @Test
    fun `erase fades the digit in 90 ms, undo and notes play nothing`() {
        val state = SudokuBoardState(SudokuSession(ref = ref, puzzle = peanut450)).apply { place(0) }
        val motion = setGrid(state)
        frames(2)

        composeRule.runOnIdle { state.erase() }
        frames(2)
        composeRule.runOnIdle {
            assertEquals(4, motion().ghostDigitOf(0))
            assertTrue("fading alpha ${motion().ghostAlpha()}", motion().ghostAlpha() in 0f..1f)
        }
        composeRule.mainClock.advanceTimeBy(SudokuMotion.ERASE_FADE_MS + 48L)
        composeRule.runOnIdle { assertEquals(0, motion().ghostDigitOf(0)) }

        composeRule.runOnIdle {
            state.undo()
            state.toggleNotesMode()
            state.select(1)
            state.input(2)
        }
        frames(2)
        composeRule.runOnIdle {
            assertEquals(1f, motion().scaleOf(0))
            assertEquals(0, motion().ghostDigitOf(0))
            assertEquals(emptyList<HapticFeedbackType>(), haptics.played)
        }
    }

    @Test
    fun `reduced motion makes the pop instant and keeps the tick`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val state = SudokuBoardState(SudokuSession(ref = ref, puzzle = peanut450))
        val motion = setGrid(state)
        frames(2)

        composeRule.runOnIdle { state.place(1) }
        frames(2)
        composeRule.runOnIdle {
            assertEquals(1f, motion().scaleOf(1))
            assertEquals(listOf(HapticFeedbackType.TextHandleMove), haptics.played)
        }
    }

    @Test
    fun `the solve sweeps, confirms, then hands off within 400 ms while the win record is immediate`() {
        val won = ArrayList<CompletionRecord>()
        val solved = ArrayList<Pair<Long, Int>>()
        val ui = SudokuPlayUi(SudokuGames.MINI, InMemorySudokuHighlightSettings())
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, { won += it })) as SudokuPlayController
        val envelope = Puzzle(ui.gameId, peanut450.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(peanut450))
        assertEquals(Adopt.Adopted, controller.adoptToday(envelope, ref))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    ui.Screen(
                        controller = controller,
                        status = PlayBoardStatus.Ready,
                        layout = PlayLayout.SinglePane,
                        sidePanel = null,
                        onRetry = {},
                        onSolved = { time, hints -> solved += time to hints },
                        modifier = Modifier,
                    )
                }
            }
        }
        val state = composeRule.runOnIdle { controller.board!! }
        composeRule.runOnIdle { openCells.dropLast(1).forEach { state.place(it) } }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { haptics.played.clear() }

        composeRule.runOnIdle { state.place(openCells.last()) }
        composeRule.runOnIdle { assertEquals("win record written at commit", 1, won.size) }
        composeRule.mainClock.advanceTimeBy(SudokuMotion.SOLVE_RUN_MS - 50L)
        composeRule.runOnIdle {
            assertEquals("hand-off waits for the sweep", emptyList<Pair<Long, Int>>(), solved)
            assertEquals(listOf(HapticFeedbackType.Confirm), haptics.played)
        }
        composeRule.mainClock.advanceTimeBy(50L + 48L)
        composeRule.runOnIdle {
            val session = state.session
            assertEquals(listOf(session.elapsedMs to session.hintsUsed), solved)
            assertEquals(listOf(HapticFeedbackType.Confirm), haptics.played)
        }
    }

    // Frame by frame: once the sweep is over nothing animates, and one big step skips past the rest timer's wake-up.
    private fun advance(ms: Long) = repeat((ms / 50L).toInt()) { composeRule.mainClock.advanceTimeBy(50L) }

    private fun solveWithRest(
        rest: Boolean,
        reduced: Boolean = false,
        beforeWin: () -> Unit = {},
    ): Triple<SudokuBoardState, List<Pair<Long, Int>>, () -> Unit> {
        if (reduced) {
            Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        }
        val solved = ArrayList<Pair<Long, Int>>()
        // Mini's own brand, as the app registers it: the frame line is drawn in its accent.
        val ui = SudokuPlayUi(SudokuGames.MINI, InMemorySudokuHighlightSettings(), MiniSudokuBrand)
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})) as SudokuPlayController
        val envelope = Puzzle(ui.gameId, peanut450.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(peanut450))
        assertEquals(Adopt.Adopted, controller.adoptToday(envelope, ref))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(
                    LocalHapticFeedback provides haptics,
                    LocalSolveRestOnBoard provides rest,
                ) {
                    ui.Screen(
                        controller = controller,
                        status = PlayBoardStatus.Ready,
                        layout = PlayLayout.SinglePane,
                        sidePanel = null,
                        onRetry = {},
                        onSolved = { time, hints -> solved += time to hints },
                        modifier = Modifier,
                    )
                }
            }
        }
        val state = composeRule.runOnIdle { controller.board!! }
        composeRule.runOnIdle { openCells.dropLast(1).forEach { state.place(it) } }
        composeRule.mainClock.advanceTimeBy(1_000)
        beforeWin()
        composeRule.runOnIdle { state.place(openCells.last()) }
        return Triple(state, solved) { Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
    }

    @Test
    fun `with the rest on the solved board stays for the solve moment, then hands off once`() {
        val (_, solved, _) = solveWithRest(rest = true)
        advance(CelebrationMotion.SolveMomentMs / 2)
        composeRule.runOnIdle { assertEquals("still resting", 0, solved.size) }
        composeRule.onNodeWithTag("play.solve_rest").assertExists()
        composeRule.onNodeWithTag("play.solve_stamp").assertExists()

        advance(CelebrationMotion.SolveMomentMs)
        composeRule.runOnIdle { assertEquals(1, solved.size) }
        composeRule.onNodeWithTag("play.solve_rest").assertDoesNotExist()
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()
        advance(5_000)
        composeRule.runOnIdle { assertEquals("one solve, one hand-off", 1, solved.size) }
    }

    @Test
    fun `a tap on the board during the rest hands off at once`() {
        val (_, solved, _) = solveWithRest(rest = true)
        advance(700)
        composeRule.onNodeWithTag("play.solve_stamp").performClick()
        composeRule.mainClock.advanceTimeBy(16)
        composeRule.runOnIdle { assertEquals("a tap on the stamp's area passes to the layer under it", 1, solved.size) }
        advance(5_000)
        composeRule.runOnIdle { assertEquals(1, solved.size) }
    }

    @Test
    fun `with the rest on the success buzz comes once as the sweep starts`() {
        val (_, solved, _) = solveWithRest(rest = true)
        advance(300)
        assertEquals(listOf(HapticFeedbackType.Confirm), haptics.played)
        advance(CelebrationMotion.SolveMomentMs * 2)
        composeRule.runOnIdle {
            assertEquals(1, solved.size)
            assertEquals("once, and the a second confirm would show as a second buzz", 1, haptics.played.count { it == HapticFeedbackType.Confirm })
        }
    }

    @Test
    fun `reduced motion keeps the success buzz`() {
        val (_, _, restore) = solveWithRest(rest = true, reduced = true)
        try {
            advance(300)
            assertEquals(listOf(HapticFeedbackType.Confirm), haptics.played)
        } finally {
            restore()
        }
    }

    /**
     * One live solve through to its hand-off: exactly one Confirm goes out as the sweep starts, and no second one
     * by the hand-off. The solve rest and the board share one recorder, so a doubled confirm shows as two.
     */
    private fun assertOneConfirm(rest: Boolean) {
        fun confirms() = haptics.played.count { it == HapticFeedbackType.Confirm }
        val (_, solved, _) = solveWithRest(rest)
        advance(300)
        assertEquals("as the sweep starts", 1, confirms())
        advance(CelebrationMotion.SolveMomentMs * 2)
        composeRule.runOnIdle {
            assertEquals(1, solved.size)
            assertEquals("no second confirm after the sweep", 1, confirms())
        }
    }

    @Test
    fun `with the rest off the solve confirms once as the sweep starts`() {
        assertOneConfirm(rest = false)
    }

    @Test
    fun `with the rest on the solve confirms once as the sweep starts`() {
        assertOneConfirm(rest = true)
    }

    @Test
    fun `reduced motion keeps the same rest`() {
        val (_, solved, restore) = solveWithRest(rest = true, reduced = true)
        try {
            advance(CelebrationMotion.SolveMomentMs / 2)
            composeRule.runOnIdle { assertEquals(0, solved.size) }
            composeRule.onNodeWithTag("play.solve_rest").assertExists()
            composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()
            advance(CelebrationMotion.SolveMomentMs)
            composeRule.runOnIdle { assertEquals(1, solved.size) }
        } finally {
            restore()
        }
    }

    private fun gridWidth(): Float = composeRule.onNodeWithTag("sudoku.grid").fetchSemanticsNode().boundsInRoot.width

    private fun gridPixels(): PixelMap = composeRule.onNodeWithTag("sudoku.grid").captureToImage().toPixelMap()

    // Inside the 3 dp frame line: the middle of the top edge, the right edge, and the left edge near its top (the lap's last stretch).
    private fun PixelMap.top(): Color = this[width / 2, 2]
    private fun PixelMap.right(): Color = this[width - 3, height / 2]
    private fun PixelMap.left(): Color = this[2, height / 4]

    /** The grid's width at every 16 ms frame for [ms] after the solve. */
    private fun gridWidths(ms: Int): List<Float> = List(ms / 16) {
        composeRule.mainClock.advanceTimeBy(16)
        gridWidth()
    }

    /** [ms] in whole frames, idle after each. Measured: bare clock steps show the rest's hand-off about 180 ms late (1696 ms against 1520 ms). */
    private fun settle(ms: Int) = repeat(ms / 16) {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    @Test
    fun `with the rest on the grid pops as the band leaves and is back at rest before the frame closes`() {
        var atRest = 0f
        solveWithRest(rest = true, beforeWin = { atRest = gridWidth() })
        val widths = gridWidths(1_296)

        assertEquals("still at rest 400 ms in", atRest, widths[400 / 16 - 1])
        val widest = widths.max()
        assertEquals("the pop's peak", CelebrationMotion.BoardPopScale, widest / atRest, 0.002f)
        val peakMs = (widths.indexOf(widest) + 1) * 16
        assertTrue("peak at $peakMs ms", peakMs in 540..620)
        assertEquals("nothing moves by 1300 ms", atRest, widths.last())
    }

    @Test
    fun `with the rest on the accent line laps the grid clockwise and is closed by 1300 ms`() {
        var before: PixelMap? = null
        solveWithRest(rest = true, beforeWin = { before = gridPixels() })
        settle(1_000)
        val lapping = gridPixels()
        settle(350)
        val closed = gridPixels()

        assertTrue("the line is on the top edge at 1000 ms", lapping.top() != before!!.top())
        assertEquals("and down the right edge", lapping.top(), lapping.right())
        assertEquals("the left edge is the lap's last stretch", before!!.left(), lapping.left())
        assertEquals(lapping.top(), closed.top())
        assertEquals("the frame is closed", closed.top(), closed.left())
    }

    @Test
    fun `flag off, the grid does not pop`() {
        var atRest = 0f
        solveWithRest(rest = false, beforeWin = { atRest = gridWidth() })
        gridWidths(1_296).forEachIndexed { frame, width -> assertEquals("frame $frame", atRest, width) }
    }

    @Test
    fun `reduced motion shows the closed frame at once and does not pop`() {
        var atRest = 0f
        var before: PixelMap? = null
        val (_, _, restore) = solveWithRest(rest = true, reduced = true, beforeWin = {
            atRest = gridWidth()
            before = gridPixels()
        })
        try {
            frames(3)
            val closed = gridPixels()
            assertTrue("the line is there", closed.top() != before!!.top())
            assertEquals(closed.top(), closed.right())
            assertEquals(closed.top(), closed.left())
            gridWidths(1_296).forEachIndexed { frame, width -> assertEquals("frame $frame", atRest, width) }
        } finally {
            restore()
        }
    }

    @Test
    fun `the solve wave is over inside the moment's motion, and the hand-off is the solve moment from the solve`() {
        val motionEnd = CelebrationMotion.FrameDrawStartMs + CelebrationMotion.FrameDrawMs
        assertEquals(1_300, motionEnd)
        for (geometry in listOf(SudokuGeometry(6, 2, 3), SudokuGeometry(9, 3, 3))) {
            assertTrue("solve wave ${SudokuMotion.solveWave(geometry).totalMs} ms", SudokuMotion.solveWave(geometry).totalMs <= motionEnd)
        }
        val (_, solved, _) = solveWithRest(rest = true)
        // Clock margin around the solve moment: whole frames, so the 16 ms settle steps can't straddle it.
        val clockMarginMs = 50
        settle(CelebrationMotion.SolveMomentMs.toInt() - clockMarginMs)
        composeRule.runOnIdle { assertEquals("still resting before the moment ends", 0, solved.size) }
        settle(2 * clockMarginMs)
        composeRule.runOnIdle { assertEquals("handed off after the solve moment", 1, solved.size) }
    }

    //endregion

    //region specs

    private fun AnimationSpec<Float>.durationMs(): Long = vectorize(Float.VectorConverter)
        .getDurationNanos(AnimationVector1D(0f), AnimationVector1D(1f), AnimationVector1D(0f)) / 1_000_000

    @Test
    fun `reduced motion gives zero-duration specs`() {
        val wave = SudokuMotion.solveWave(SudokuGeometry(9, 3, 3))
        assertEquals(0L, SudokuMotion.pop(reduced = true).durationMs())
        assertEquals(0L, SudokuMotion.eraseFade(reduced = true).durationMs())
        assertEquals(0L, SudokuMotion.wave(wave, reduced = true).durationMs())
    }

    @Test
    fun `full motion runs inside the A12 budgets`() {
        assertTrue(SudokuMotion.pop(reduced = false).durationMs() > 0L)
        assertEquals(SudokuMotion.ERASE_FADE_MS.toLong(), SudokuMotion.eraseFade(reduced = false).durationMs())
        for (geometry in listOf(SudokuGeometry(6, 2, 3), SudokuGeometry(9, 3, 3))) {
            val corner = geometry.cellCount - 1
            val everyUnit = listOf(UnitKind.ROW, UnitKind.COLUMN, UnitKind.BOX).map { SudokuUnit(it, geometry.size - 1) }
            val unit = SudokuMotion.unitWave(geometry, corner, everyUnit)
            val solve = SudokuMotion.solveWave(geometry)
            assertTrue("unit wave ${unit.totalMs} ms", unit.totalMs < 600)
            assertTrue("solve wave ${solve.totalMs} ms", solve.totalMs <= SudokuMotion.SOLVE_RUN_MS)
            assertEquals(solve.totalMs.toLong(), SudokuMotion.wave(solve, reduced = false).durationMs())
        }
    }

    //endregion

    //region events and haptics, pure

    private fun transition(state: SudokuBoardState, action: SudokuBoardState.() -> Unit): SudokuPlayerEvent {
        val before = state.session
        state.action()
        return sudokuPlayerEvent(before, state.session)
    }

    @Test
    fun `only new commits are events, never undo, redo, notes or the notes toggle`() {
        val state = SudokuBoardState(SudokuSession(ref = ref, puzzle = peanut450))

        assertEquals(SudokuPlayerEvent.Placed(0, emptyList()), transition(state) { place(0) })
        assertEquals(SudokuPlayerEvent.None, transition(state) { undo() })
        assertEquals(SudokuPlayerEvent.None, transition(state) { redo() })
        assertEquals(SudokuPlayerEvent.Erased(0, 4), transition(state) { erase() })
        assertEquals(SudokuPlayerEvent.None, transition(state) { toggleNotesMode() })
        assertEquals(SudokuPlayerEvent.None, transition(state) { place(1, digit = 2) })
        assertEquals(SudokuPlayerEvent.None, transition(state) { toggleNotesMode() })
        assertEquals(SudokuPlayerEvent.None, transition(state) { select(3) })
    }

    @Test
    fun `a place that closes a conflict-free unit names it, and the last place is the solve`() {
        val state = SudokuBoardState(SudokuSession(ref = ref, puzzle = peanut450))
        listOf(0, 1, 3).forEach { state.place(it) }

        assertEquals(SudokuPlayerEvent.Placed(5, listOf(SudokuUnit(UnitKind.ROW, 0))), transition(state) { place(5) })
        openCells.drop(4).dropLast(1).forEach { state.place(it) }
        assertEquals(SudokuPlayerEvent.Solved(openCells.last()), transition(state) { place(openCells.last()) })
    }

    //endregion

    private class RecordingHaptics : HapticFeedback {
        val played = ArrayList<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }
}

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
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, LocalDate.of(2026, 11, 4), 450)
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
        val ui = SudokuPlayUi(SudokuGames.MINI)
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, { won += it })) as SudokuPlayController
        val envelope = Puzzle(ui.gameId, peanut450.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(peanut450))
        assertEquals(Adopt.Adopted, controller.adoptToday(envelope, ref))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    ui.Screen(
                        controller = controller,
                        status = PlayBoardStatus.Ready(banner = null),
                        layout = PlayLayout.SinglePane,
                        onRetry = {},
                        onSolved = { time, hints -> solved += time to hints },
                        onHowToPlay = null,
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
            assertEquals(listOf(HapticFeedbackType.TextHandleMove), haptics.played)
        }
        composeRule.mainClock.advanceTimeBy(50L + 48L)
        composeRule.runOnIdle {
            val session = state.session
            assertEquals(listOf(session.elapsedMs to session.hintsUsed), solved)
            assertEquals(listOf(HapticFeedbackType.TextHandleMove, HapticFeedbackType.Confirm), haptics.played)
        }
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
    fun `each player event maps to its haptic`() {
        assertEquals(SudokuHaptic.Tick, sudokuHapticFor(SudokuPlayerEvent.Placed(0, emptyList())))
        assertEquals(SudokuHaptic.Confirm, sudokuHapticFor(SudokuPlayerEvent.Placed(0, listOf(SudokuUnit(UnitKind.ROW, 0)))))
        assertEquals(SudokuHaptic.Tick, sudokuHapticFor(SudokuPlayerEvent.Solved(0)))
        assertNull(sudokuHapticFor(SudokuPlayerEvent.Erased(0, 4)))
        assertNull(sudokuHapticFor(SudokuPlayerEvent.None))
        assertEquals(HapticFeedbackType.TextHandleMove, SudokuHaptic.Tick.feedbackType())
        assertEquals(HapticFeedbackType.Confirm, SudokuHaptic.Confirm.feedbackType())
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

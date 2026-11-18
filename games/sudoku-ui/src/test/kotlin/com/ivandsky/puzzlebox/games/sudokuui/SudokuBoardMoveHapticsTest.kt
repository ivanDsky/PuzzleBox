package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
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
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuBoardMoveHapticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val peanut450 = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))
    private val openCells = givens.indices.filter { givens[it] == 0 }
    private val ui = SudokuPlayUi(SudokuGames.MINI, InMemorySudokuHighlightSettings())
    private val played = ArrayList<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }
    private lateinit var controller: SudokuPlayController

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private fun SudokuBoardState.place(idx: Int, digit: Int = solution[idx]) {
        select(idx)
        input(digit)
    }

    private fun setScreen() {
        controller = ui.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})) as SudokuPlayController
        val envelope = Puzzle(ui.gameId, peanut450.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(peanut450))
        assertEquals(Adopt.Adopted, controller.adoptToday(envelope, ref))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(
                    LocalHapticFeedback provides haptics,
                    LocalSolveRestOnBoard provides false,
                ) {
                    ui.Screen(
                        controller = controller,
                        status = PlayBoardStatus.Ready,
                        layout = PlayLayout.SinglePane,
                        sidePanel = null,
                        onRetry = {},
                        onSolved = { _, _ -> },
                        modifier = Modifier,
                    )
                }
            }
        }
    }

    private fun board(): SudokuBoardState = composeRule.runOnIdle { controller.board!! }

    @Test
    fun `a placed digit ticks once and undo plays nothing`() {
        setScreen()
        val state = board()
        composeRule.runOnIdle { state.place(0) }
        composeRule.runOnIdle { assertEquals(listOf(HapticFeedbackType.TextHandleMove), played) }
        composeRule.runOnIdle { state.undo() }
        composeRule.runOnIdle { assertEquals(listOf(HapticFeedbackType.TextHandleMove), played) }
    }

    @Test
    fun `the solving entry plays only the solve confirm`() {
        setScreen()
        val state = board()
        composeRule.runOnIdle { openCells.dropLast(1).forEach { state.place(it) } }
        composeRule.runOnIdle { played.clear() }
        composeRule.runOnIdle { state.place(openCells.last()) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(listOf(HapticFeedbackType.Confirm), played) }
    }

    @Test
    fun `a restored board plays nothing on first composition`() {
        val placed = SudokuBoardState(SudokuSession(ref = ref, puzzle = peanut450)).apply { place(0) }.session
        val restored = SudokuBoardState(placed)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalHapticFeedback provides haptics,
            ) {
                rememberSudokuMotion(restored)
            }
        }
        composeRule.runOnIdle { assertEquals(emptyList<HapticFeedbackType>(), played) }
    }

    @Test
    fun `a refusal on a new board after play again buzzes again`() {
        setScreen()
        val first = board()
        composeRule.runOnIdle {
            first.select(2)
            first.input(5)
        }
        composeRule.runOnIdle { assertEquals(listOf(HapticFeedbackType.Reject), played) }
        composeRule.runOnIdle { controller.playAgain() }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val second = composeRule.runOnIdle { controller.board!!.also { assertFalse(it === first) } }
        composeRule.runOnIdle {
            second.select(2)
            second.input(5)
        }
        composeRule.runOnIdle { assertEquals(listOf(HapticFeedbackType.Reject, HapticFeedbackType.Reject), played) }
    }
}

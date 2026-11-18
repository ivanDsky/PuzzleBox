package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S2 in the lessons: the tutorial host confirms each lesson's solve once, whatever the game's board
 * does on its own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TutorialHostSolveConfirmTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = LessonGame(lessons = 3)
    private val session = TutorialSession(game.tutorial)
    private val played = ArrayList<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }

    /** The stand-in game with a board that begins its own solve rest on the win, as Mini Sudoku's does. */
    private val restingBoardGame = object : GamePlayUi by game {
        @Composable
        override fun Board(controller: GamePlayController, modifier: Modifier) {
            val rest = rememberSolveRest()
            val solved = controller.isComplete
            LaunchedEffect(solved) { if (solved) rest.begin(this) }
            game.Board(controller, modifier)
        }
    }

    private fun setHost(ui: GamePlayUi = game, restOnBoard: Boolean = false) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalHapticFeedback provides haptics,
                    LocalHapticFeedback provides haptics,
                    LocalSolveRestOnBoard provides restOnBoard,
                ) {
                    TutorialHost(ui = ui, session = session, onFinished = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Wins the current lesson and waits out its success beat. */
    private fun solveLesson() {
        composeRule.onNodeWithTag("lesson.solve").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(PAST_THE_BEAT_MS)
        composeRule.waitForIdle()
    }

    @Test
    fun `each lesson solve confirms once`() {
        setHost()

        solveLesson()
        assertEquals(listOf(HapticFeedbackType.Confirm), played)

        solveLesson()
        assertEquals(List(2) { HapticFeedbackType.Confirm }, played)
    }

    @Test
    fun `a board that confirms its own solve does not double the host's`() {
        setHost(ui = restingBoardGame)

        solveLesson()

        assertEquals(listOf(HapticFeedbackType.Confirm), played)
    }

    @Test
    fun `a board that rests does not double the host's either`() {
        setHost(ui = restingBoardGame, restOnBoard = true)

        solveLesson()

        assertEquals(listOf(HapticFeedbackType.Confirm), played)
    }

    @Test
    fun `a lesson that comes back already solved does not confirm again`() {
        session.advance()

        setHost()
        composeRule.mainClock.advanceTimeBy(PAST_THE_BEAT_MS)
        composeRule.waitForIdle()

        assertEquals(emptyList<HapticFeedbackType>(), played)
    }

    private companion object {
        const val PAST_THE_BEAT_MS = 1_000L
    }
}

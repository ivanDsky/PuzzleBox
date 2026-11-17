package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.LocalPlayFeel
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S2 on the real play board: with PlayFeel on a tapped cell ticks; with it off the board is as silent as before. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class TangoBoardMoveHapticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Row 1, column 3 of the fixture: an open (non-given) cell. */
    private val idx = 2
    private val cell = "Row 1, column 3"

    private val played = ArrayList<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }

    private val controller = TangoPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = {}),
        hintMode = HintMode.Stepwise(Dispatchers.Unconfined),
    ).also {
        assertEquals(Adopt.Adopted, it.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(LocalDate.parse("2026-11-16"))))
    }

    private fun showBoard(playFeel: Boolean) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics, LocalPlayFeel provides playFeel) {
                    TangoPlayUi(HintMode.Stepwise(UnconfinedTestDispatcher())).Board(controller, Modifier.fillMaxWidth())
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tapCell() {
        composeRule.onNodeWithContentDescription(cell, substring = true).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    @Test
    fun `with PlayFeel on a tapped cell ticks and undo is silent`() {
        showBoard(playFeel = true)

        tapCell()
        assertEquals(listOf(HapticFeedbackType.TextHandleMove), played)

        composeRule.runOnIdle { controller.undo() }
        composeRule.waitForIdle()
        assertEquals(listOf(HapticFeedbackType.TextHandleMove), played)
    }

    @Test
    fun `a board that opens with marks on it plays nothing`() {
        controller.onPlacement(TangoPlacement(idx, TangoCell.MOON))

        showBoard(playFeel = true)

        assertEquals(emptyList<HapticFeedbackType>(), played)
    }

    @Test
    fun `with PlayFeel off the board stays silent`() {
        showBoard(playFeel = false)

        tapCell()

        assertEquals("the tap landed", TangoCell.SUN, composeRule.runOnIdle { controller.session!!.grid()[idx] })
        assertEquals(emptyList<HapticFeedbackType>(), played)
    }
}

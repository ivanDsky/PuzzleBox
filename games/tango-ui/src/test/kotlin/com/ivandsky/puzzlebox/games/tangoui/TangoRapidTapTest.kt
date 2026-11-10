package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.gameui.Adopt
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

/**
 * Device feedback (M4-tango-28): fast repeat taps on one cell were dropped in play and
 * test solve. Both run the real play board ([TangoPlayUi.Board]), so these drive it with
 * the real controller: a tap must read the live session, not the model the board last
 * composed with, and a recomposition mid-gesture must not restart the tap detector.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class TangoRapidTapTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Row 1, column 3 of the fixture: an open (non-given) cell. */
    private val idx = 2
    private val cell = "Row 1, column 3"

    private val controller = TangoPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = {}),
        hintMode = HintMode.Stepwise(Dispatchers.Unconfined),
    ).also {
        assertEquals(Adopt.Adopted, it.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(LocalDate.parse("2026-11-09"))))
    }

    private fun showBoard() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TangoPlayUi(HintMode.Stepwise(UnconfinedTestDispatcher())).Board(controller, Modifier.fillMaxWidth())
            }
        }
        composeRule.waitForIdle()
    }

    private fun valueAt(i: Int): TangoCell? = composeRule.runOnIdle { controller.session!!.grid()[i] }

    @Test
    fun `two taps on one empty cell inside one frame land sun then moon`() {
        showBoard()
        composeRule.mainClock.autoAdvance = false // no frame, so no recomposition, between the taps

        composeRule.onNodeWithContentDescription(cell, substring = true).performTouchInput {
            click()
            click()
        }

        assertEquals(TangoCell.MOON, controller.session!!.grid()[idx])
    }

    @Test
    fun `a recomposition mid-tap does not drop the tap`() {
        showBoard()
        val node = composeRule.onNodeWithContentDescription(cell, substring = true)

        node.performTouchInput { down(center) }
        // Another placement lands while the finger is down: the board recomposes with a new model.
        composeRule.runOnIdle { controller.onPlacement(TangoPlacement(3, TangoCell.SUN)) }
        composeRule.waitForIdle()
        node.performTouchInput { up() }

        assertEquals(TangoCell.SUN, valueAt(3)) // the mid-tap placement really landed
        assertEquals(TangoCell.SUN, valueAt(idx))
    }
}

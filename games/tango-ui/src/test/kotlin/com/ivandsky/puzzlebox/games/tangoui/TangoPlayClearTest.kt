package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.tango.TangoCell
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tango's play row carries the same always-enabled Clear as every other board game. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TangoPlayClearTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val controller = TangoPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
        hintMode = HintMode.Stepwise(Dispatchers.Unconfined),
    ).also {
        check(it.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(LocalDate.of(2026, 10, 31))) == Adopt.Adopted)
    }

    private fun show(layout: PlayLayout = PlayLayout.SinglePane) {
        composeRule.setContent {
            PuzzleBoxTheme {
                TangoPlayScreen(
                    controller = controller,
                    status = PlayBoardStatus.Ready(null),
                    layout = layout,
                    sidePanel = null,
                    onRetry = {},
                    onSolved = { _, _ -> },
                )
            }
        }
    }

    @Test
    fun `Clear is present and enabled on an empty board`() {
        show()
        composeRule.onNodeWithTag("play.clear").assertExists().assertIsEnabled()
    }

    @Test
    fun `tapping Clear on an empty board changes nothing`() {
        show()
        val before = controller.session
        composeRule.onNodeWithTag("play.clear").performClick()
        composeRule.waitForIdle()
        assertEquals(before, controller.session)
    }

    @Test
    fun `tapping Clear wipes the placements and undo brings them back`() {
        show()
        composeRule.runOnIdle { controller.onPlacement(TangoPlacement(2, TangoCell.MOON)) }

        composeRule.onNodeWithTag("play.clear").performClick()
        composeRule.waitForIdle()
        assertEquals(emptyMap<Int, TangoCell>(), controller.session?.placements)

        composeRule.onNodeWithTag("play.undo").performClick()
        composeRule.waitForIdle()
        assertEquals(mapOf(2 to TangoCell.MOON), controller.session?.placements)
    }

    @Test
    fun `Clear is enabled on a solved board too`() {
        show()
        composeRule.runOnIdle { TangoPlayFixture.placements.forEach { (idx, v) -> controller.onPlacement(TangoPlacement(idx, v)) } }
        composeRule.onNodeWithTag("play.clear").assertExists().assertIsEnabled()
    }
}

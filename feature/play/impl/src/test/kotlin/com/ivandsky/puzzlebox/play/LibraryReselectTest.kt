package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tab reselect on the Games root scrolls the games list back to its top. The list runs on
 * a short phone window, so the header scrolls out of view first; the plain form scrolls the
 * whole list, the tabbed form scrolls the Dailies body while the header stays put.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h300dp")
class LibraryReselectTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a tab reselect scrolls the games list back to its header`() {
        val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        setContent(reselect, tabbed = false)
        composeRule.onNodeWithText("Mini Sudoku").performScrollTo()
        composeRule.onNodeWithText("Games").assertIsNotDisplayed()

        composeRule.runOnIdle { reselect.tryEmit(Unit) }

        composeRule.onNodeWithText("Games").assertIsDisplayed()
    }

    @Test
    fun `a tab reselect scrolls the tabbed dailies list back to its first row`() {
        val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        setContent(reselect, tabbed = true)
        composeRule.onNodeWithText("Mini Sudoku").performScrollTo()
        composeRule.onNodeWithText("Zip").assertIsNotDisplayed()

        composeRule.runOnIdle { reselect.tryEmit(Unit) }

        composeRule.onNodeWithText("Zip").assertIsDisplayed()
    }

    private fun setContent(reselect: SharedFlow<Unit>, tabbed: Boolean) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides reselect) {
                PuzzleBoxTheme {
                    ListHost(tabbed)
                }
            }
        }
    }
}

@Composable
private fun ListHost(tabbed: Boolean) {
    val scroll = rememberScrollState()
    LibraryList(
        state = previewState(),
        layout = PlayLayout.SinglePane,
        scroll = scroll,
        onRow = {},
        modifier = Modifier,
        duels = if (tabbed) DuelsSlot(content = {}, needYou = { 0 }) else null,
        tab = GamesTab.Dailies,
    )
}

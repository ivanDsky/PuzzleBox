package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
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
 * whole list, the tabbed form scrolls the Dailies body while the header stays put, and on the
 * Duels tab it scrolls the Duels body's own list.
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

    @Test
    fun `a tab reselect scrolls the Duels list back to its first item`() {
        val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        setContent(reselect, tabbed = true, tab = GamesTab.Duels)
        composeRule.onNodeWithTag("duels.list").performScrollToIndex(DUEL_COUNT - 1)
        composeRule.onNodeWithText("duel 0").assertIsNotDisplayed()

        composeRule.runOnIdle { reselect.tryEmit(Unit) }

        composeRule.onNodeWithText("duel 0").assertIsDisplayed()
    }

    private fun setContent(reselect: SharedFlow<Unit>, tabbed: Boolean, tab: GamesTab = GamesTab.Dailies) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides reselect) {
                PuzzleBoxTheme {
                    ListHost(tabbed, tab)
                }
            }
        }
    }

    private companion object {
        const val DUEL_COUNT = 30
    }
}

/** A Duels body that is a long list of its own, laid out on the state the list hands it. */
private val scrollingDuels = DuelsSlot(
    content = { state ->
        LazyColumn(Modifier.testTag("duels.list"), state = state) {
            items(30) { Text("duel $it", Modifier.height(48.dp)) }
        }
    },
    needYou = { 0 },
)

@Composable
private fun ListHost(tabbed: Boolean, tab: GamesTab) {
    val scroll = rememberScrollState()
    LibraryList(
        state = previewState(),
        layout = PlayLayout.SinglePane,
        scroll = scroll,
        onRow = {},
        modifier = Modifier,
        duels = if (tabbed) scrollingDuels else null,
        tab = tab,
    )
}

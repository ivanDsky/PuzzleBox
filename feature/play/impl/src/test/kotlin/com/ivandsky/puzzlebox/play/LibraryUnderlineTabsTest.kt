package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Games tabs (nav-rethink 125): the 120 strip over the
 * 121 pager, one swipeable page per tab with its own scroll, the Duels badge in the strip, and a horizontal drag on
 * the Custom list's chips that scrolls the chips and not the tab.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h500dp")
class LibraryUnderlineTabsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var needYou by mutableIntStateOf(0)
    private var tab by mutableStateOf(GamesTab.Dailies)
    private val reported = mutableListOf<GamesTab>()

    private fun show(customCount: Int = 0) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides reselect) {
                PuzzleBoxTheme {
                    Host(customCount, needYou = { needYou }, tab = tab, onTab = { tab = it; reported += it })
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun swipeToNext() {
        composeRule.onNodeWithTag("underline.pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
    }

    @Test
    fun `draws the underline strip over the pager with the tab tags and roles kept`() {
        show()

        composeRule.onNodeWithTag("underline.tabs").assertExists()
        composeRule.onNodeWithTag("underline.pager").assertExists()
        listOf("dailies", "duels", "custom").forEach { composeRule.onNodeWithTag("library.tab.$it").assertExists() }
        composeRule.onNodeWithTag("library.tab.dailies").assertIsSelected()
        composeRule.onNodeWithText("Zip").assertIsDisplayed()
    }

    @Test
    fun `swipes Dailies, Duels, Custom and reports each tab`() {
        show()

        swipeToNext()
        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()
        swipeToNext()
        composeRule.onNodeWithTag("library.tab.custom").assertIsSelected()
        composeRule.onNodeWithTag("custom.chips").assertIsDisplayed()
        composeRule.onNodeWithTag("library.tab.friends").assertDoesNotExist()
        assertEquals(listOf(GamesTab.Duels, GamesTab.Custom), reported)
    }

    @Test
    fun `a tap on a tab moves to its page`() {
        show()

        composeRule.onNodeWithTag("library.tab.duels").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()
        composeRule.onNodeWithText("duel 0").assertIsDisplayed()
    }

    @Test
    fun `the Duels badge and the Custom count show in the strip and name themselves`() {
        needYou = 3
        show(customCount = 2)

        composeRule.onNodeWithTag("library.tab.duels.count", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("library.tab.duels").assertContentDescriptionEquals("Duels, 3 need you")
        composeRule.onNodeWithTag("library.tab.custom.count", useUnmergedTree = true).assertExists()

        needYou = 12
        composeRule.onNodeWithTag("library.tab.duels").assertContentDescriptionEquals("Duels, 12 need you")
        composeRule.onNodeWithText("9+", useUnmergedTree = true).assertExists()

        needYou = 0
        composeRule.onNodeWithTag("library.tab.duels.count", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `each tab keeps its own scroll position across a swipe there and back`() {
        show()
        composeRule.onNodeWithText("Mini Sudoku").performScrollTo()
        composeRule.onNodeWithText("Zip").assertIsNotDisplayed()

        composeRule.onNodeWithTag("library.tab.duels").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("duels.list").performScrollToIndex(DUEL_COUNT - 1)
        composeRule.onNodeWithTag("library.tab.dailies").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Mini Sudoku").assertIsDisplayed()
        composeRule.onNodeWithText("Zip").assertIsNotDisplayed()

        composeRule.onNodeWithTag("library.tab.duels").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("duel 0").assertIsNotDisplayed()
    }

    @Test
    fun `a tap on the current tab scrolls it back to the top and keeps the page`() {
        show()
        composeRule.onNodeWithText("Mini Sudoku").performScrollTo()
        composeRule.onNodeWithText("Zip").assertIsNotDisplayed()

        composeRule.onNodeWithTag("library.tab.dailies").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Zip").assertIsDisplayed()
        composeRule.onNodeWithTag("library.tab.dailies").assertIsSelected()
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `a request for another tab moves the pager there`() {
        show()

        tab = GamesTab.Duels
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()
        composeRule.onNodeWithText("duel 0").assertIsDisplayed()
    }

    @Test
    fun `a horizontal drag on the Custom chips scrolls the chips and does not change the tab`() {
        show()
        composeRule.onNodeWithTag("library.tab.custom").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("chip 0").assertIsDisplayed()
        reported.clear()

        composeRule.onNodeWithTag("custom.chips").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("library.tab.custom").assertIsSelected()
        composeRule.onNodeWithText("chip 0").assertIsNotDisplayed()
        assertTrue("the drag changed the tab: $reported", reported.isEmpty())
    }

    private companion object {
        const val DUEL_COUNT = 30
    }
}

private val standInDuels: (() -> Int) -> DuelsSlot = { needYou ->
    DuelsSlot(
        content = { state, _, _ ->
            LazyColumn(Modifier.testTag("duels.list"), state = state) {
                items(30) { Text("duel $it", Modifier.height(48.dp)) }
            }
        },
        needYou = { needYou() },
        onOpenDuel = {},
        pane = { _ -> },
    )
}

@Composable
private fun Host(
    customCount: Int,
    needYou: () -> Int,
    tab: GamesTab,
    onTab: (GamesTab) -> Unit,
) {
    val scroll = rememberScrollState()
    val duels = remember { standInDuels(needYou) }
    LibraryList(
        state = previewState(),
        layout = PlayLayout.SinglePane,
        scroll = scroll,
        onRow = {},
        modifier = Modifier,
        duels = duels,
        tab = tab,
        onTab = onTab,
        customTab = {
            LazyColumn(Modifier.fillMaxWidth(), state = rememberLazyListState()) {
                item {
                    LazyRow(Modifier.testTag("custom.chips")) {
                        items(20) { Text("chip $it", Modifier.width(120.dp)) }
                    }
                }
            }
        },
        customCount = customCount,
    )
}

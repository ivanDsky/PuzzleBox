package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.test.getBoundsInRoot
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
 * Games with the underline tabs (nav-rethink 125): the 120 strip over the
 * 121 pager, one swipeable page per tab (Dailies and Duels, and Train when the app hands in a slot) with its own
 * scroll, and the Duels badge in the strip.
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

    private fun show(train: TrainSlot? = null) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides reselect) {
                PuzzleBoxTheme {
                    Host(needYou = { needYou }, tab = tab, onTab = { tab = it; reported += it }, train = train)
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
        listOf("dailies", "duels").forEach { composeRule.onNodeWithTag("library.tab.$it").assertExists() }
        composeRule.onNodeWithTag("library.tab.friends").assertDoesNotExist()
        composeRule.onNodeWithTag("library.tab.custom").assertDoesNotExist()
        composeRule.onNodeWithTag("library.tab.dailies").assertIsSelected()
        composeRule.onNodeWithText("Zip").assertIsDisplayed()
    }

    @Test
    fun `swipes Dailies to Duels and reports the tab`() {
        show()

        swipeToNext()

        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()
        composeRule.onNodeWithText("duel 0").assertIsDisplayed()
        assertEquals(listOf(GamesTab.Duels), reported)
    }

    @Test
    fun `no Train tab without a Train slot`() {
        show()

        composeRule.onNodeWithTag("library.tab.train").assertDoesNotExist()
        composeRule.onNodeWithTag("library.tab.dailies").assertExists()
        composeRule.onNodeWithTag("library.tab.duels").assertExists()
        swipeToNext()
        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()
        composeRule.onNodeWithText("train stand-in").assertDoesNotExist()
    }

    @Test
    fun `a Train slot adds the Train tab after Duels and a tap opens its body`() {
        show(train = standInTrain)

        listOf("dailies", "duels", "train").forEach { composeRule.onNodeWithTag("library.tab.$it").assertExists() }
        val duelsLeft = composeRule.onNodeWithTag("library.tab.duels").getBoundsInRoot().left
        val trainLeft = composeRule.onNodeWithTag("library.tab.train").getBoundsInRoot().left
        assertTrue("Train follows Duels in the strip: $duelsLeft, $trainLeft", trainLeft > duelsLeft)

        composeRule.onNodeWithTag("library.tab.train").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("library.tab.train").assertIsSelected()
        composeRule.onNodeWithText("train stand-in").assertIsDisplayed()
        assertEquals(GamesTab.Train, tab)
    }

    @Test
    fun `swipes Duels to Train`() {
        show(train = standInTrain)

        swipeToNext()
        swipeToNext()

        composeRule.onNodeWithTag("library.tab.train").assertIsSelected()
        composeRule.onNodeWithText("train stand-in").assertIsDisplayed()
        assertEquals(listOf(GamesTab.Duels, GamesTab.Train), reported)
    }

    @Test
    fun `a saved Train tab with no slot shows Dailies`() {
        tab = GamesTab.Train
        show()

        composeRule.onNodeWithTag("library.tab.train").assertDoesNotExist()
        composeRule.onNodeWithTag("library.tab.dailies").assertIsSelected()
        composeRule.onNodeWithText("Zip").assertIsDisplayed()
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
    fun `the Duels badge shows in the strip and names itself`() {
        needYou = 3
        show()

        composeRule.onNodeWithTag("library.tab.duels.count", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("library.tab.duels").assertContentDescriptionEquals("Duels, 3 need you")

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
    fun `the bottom bar reselect scrolls the Duels list, which has its own scroll`() {
        show()
        composeRule.onNodeWithTag("library.tab.duels").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("duels.list").performScrollToIndex(DUEL_COUNT - 1)
        composeRule.onNodeWithText("duel 0").assertIsNotDisplayed()

        composeRule.runOnIdle { reselect.tryEmit(Unit) }

        composeRule.onNodeWithText("duel 0").assertIsDisplayed()
    }

    @Test
    fun `a request for another tab moves the pager there`() {
        show()

        tab = GamesTab.Duels
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("library.tab.duels").assertIsSelected()
        composeRule.onNodeWithText("duel 0").assertIsDisplayed()
    }

    private companion object {
        const val DUEL_COUNT = 30
    }
}

private val standInTrain = TrainSlot { Text("train stand-in") }

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
    needYou: () -> Int,
    tab: GamesTab,
    onTab: (GamesTab) -> Unit,
    train: TrainSlot?,
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
        train = train,
    )
}

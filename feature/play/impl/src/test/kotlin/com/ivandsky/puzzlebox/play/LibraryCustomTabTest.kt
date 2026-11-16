package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.FriendStatus
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Games screen handed a Custom slot, on the phone: the pills read Dailies, Duels, Custom;
 * Custom shows the slot's list with the friend puzzles where the slot places them, and its
 * pill counts the ones not played yet. A request for the Friends section lands on Custom, and
 * a section request, a reselect and a return to the tab find the slot's list at its top.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LibraryCustomTabTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var friends by mutableStateOf(FriendPreviewCards.take(2))
    private var request by mutableStateOf<LibraryRequest?>(null)
    private val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val opened = ArrayList<ShareCode>()
    private val removed = ArrayList<ShareCode>()
    private var enterCodeTaps = 0

    /** What the slot was last told about the friend puzzles. */
    private var friendsEmptySeen: Boolean? = null

    /** How many rows of its own the stand-in list shows above and below the friend puzzles. */
    private var ownRows = 2

    /** A stand-in for the create module's list: its own rows, the friend rows, more of its own. */
    private val custom = CustomSlot { state, friendsEmpty, friendRows ->
        SideEffect { friendsEmptySeen = friendsEmpty }
        LazyColumn(Modifier.fillMaxSize().testTag(CUSTOM_LIST), state = state) {
            items(ownRows) { Text("draft $it", Modifier.testTag(draft(it)).height(48.dp)) }
            friendRows()
            items(ownRows) { Text("published $it", Modifier.testTag(published(it)).height(48.dp)) }
        }
    }

    private fun setContent() = composeRule.setContent {
        CompositionLocalProvider(LocalTabReselect provides reselect) {
            PuzzleBoxTheme(darkTheme = false) {
                LibraryScreen(
                    state = previewState(),
                    page = null,
                    month = null,
                    layout = PlayLayout.SinglePane,
                    hasLesson = { false },
                    onSelect = {},
                    onPlay = { _, _ -> },
                    onRetry = {},
                    onOpenLesson = {},
                    calendar = CalendarActions(),
                    onOpenRanks = { _, _ -> },
                    onEnterCode = { enterCodeTaps++ },
                    friends = friends,
                    onOpenFriend = { opened.add(it) },
                    onRemoveFriend = { removed.add(it) },
                    duels = DUELS,
                    custom = custom,
                    request = request,
                )
            }
        }
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    private fun left(tag: String) = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().left

    private fun top(tag: String) = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    @Test
    fun `with a Custom slot the pills read Dailies, Duels, Custom`() {
        setContent()

        assertTrue("Dailies is left of Duels", left("library.tab.dailies") < left("library.tab.duels"))
        assertTrue("Duels is left of Custom", left("library.tab.duels") < left("library.tab.custom"))
        composeRule.onNodeWithTag("library.tab.friends").assertDoesNotExist()
    }

    @Test
    fun `Custom shows the slot's list with the friend puzzles where the slot places them`() {
        setContent()

        tap("library.tab.custom")

        composeRule.onNodeWithTag("library.tab.custom").assertIsSelected()
        composeRule.onNodeWithTag("library.row.zip").assertDoesNotExist()
        val tops = listOf(draft(1), row(K7), row(B4), published(0)).map(::top)
        assertEquals("the friend puzzles sit between the slot's own rows, in order", tops.sorted(), tops)
        assertEquals(tops.size, tops.distinct().size)
        assertEquals(false, friendsEmptySeen)
    }

    @Test
    fun `a friend puzzle in Custom opens on a tap and is removed from its long-press menu`() {
        setContent()
        tap("library.tab.custom")

        tap(row(K7))
        assertEquals(listOf(K7), opened)

        composeRule.onNodeWithTag(row(B4)).performTouchInput { longClick() }
        tap("library.friends.${B4.value}.remove")
        assertEquals(listOf(B4), removed)
    }

    @Test
    fun `with no friend puzzles the slot is told so and the section offers Enter code`() {
        friends = emptyList()
        setContent()

        tap("library.tab.custom")

        assertEquals(true, friendsEmptySeen)
        assertTrue("the hint sits where the friend puzzles would", top(draft(1)) < top(ENTER_CODE) && top(ENTER_CODE) < top(published(0)))
        tap(ENTER_CODE)
        assertEquals(1, enterCodeTaps)
    }

    @Test
    fun `the Custom pill counts the friend puzzles not played yet`() {
        friends = FriendPreviewCards // five, one of them new
        setContent()

        composeRule.onNodeWithTag(PILL_COUNT, useUnmergedTree = true).assertTextEquals("1")
        composeRule.onNodeWithTag("library.tab.custom").assertContentDescriptionEquals("Custom, 1 new from friends")

        friends = FriendPreviewCards.filter { it.row.status != FriendStatus.New }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PILL_COUNT, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a request for the Friends section opens Custom`() {
        setContent()

        request = LibraryRequest.Section(GamesTab.Friends)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("library.tab.custom").assertIsSelected()
        composeRule.onNodeWithTag(CUSTOM_LIST).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h400dp")
    fun `a section request leaves the scrolled Custom list at its first row`() {
        showCustomScrolledDown()

        request = LibraryRequest.Section(GamesTab.Custom)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(draft(0)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h400dp")
    fun `a tab reselect scrolls the Custom list back to its first row`() {
        showCustomScrolledDown()

        composeRule.runOnIdle { reselect.tryEmit(Unit) }

        composeRule.onNodeWithTag(draft(0)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h400dp")
    fun `leaving a scrolled Custom tab and coming back finds its list at the top`() {
        showCustomScrolledDown()

        tap("library.tab.dailies")
        tap("library.tab.custom")

        composeRule.onNodeWithTag(draft(0)).assertIsDisplayed()
    }

    private fun showCustomScrolledDown() {
        ownRows = LONG
        setContent()
        tap("library.tab.custom")
        composeRule.onNodeWithTag(CUSTOM_LIST).performScrollToIndex(LONG * 2)
        composeRule.onNodeWithTag(draft(0)).assertIsNotDisplayed()
    }

    private companion object {
        const val CUSTOM_LIST = "fake.custom.list"
        const val ENTER_CODE = "library.friends.enter_code"
        const val PILL_COUNT = "library.tab.custom.count"
        const val LONG = 30

        val K7 = checkNotNull(ShareCode.parse("K7Q2MX"))
        val B4 = checkNotNull(ShareCode.parse("B4R9TZ"))

        val DUELS = DuelsSlot(content = { _, _, _ -> Text("duels body") }, needYou = { 0 }, onOpenDuel = {})

        fun draft(index: Int) = "fake.draft.$index"
        fun published(index: Int) = "fake.published.$index"
        fun row(code: ShareCode) = "library.friends.row.${code.value}"
    }
}

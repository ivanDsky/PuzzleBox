package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityPhase
import com.ivandsky.puzzlebox.play.community.CommunityRowModel
import com.ivandsky.puzzlebox.play.community.CommunityState
import com.ivandsky.puzzlebox.play.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.play.domain.FriendStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The phone's Community (nav-rethink M12-4, ticket 123): New today, Friends and Yours as three swipeable tabs under the
 * head. Each tab holds only its own rows on its own list; the filter chips are New today's alone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class CommunityTabsTest {

    @get:Rule
    val rule = createComposeRule()

    private val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var state by mutableStateOf(LOADED)
    private var friends by mutableStateOf(FRIENDS)
    private var sharePending by mutableStateOf(false)

    /** The app's slot with a Yours list like create's, plus a row that scrolls sideways (see the report: chips). */
    private val slot = CustomSlot(
        yours = { listState ->
            LazyColumn(Modifier.fillMaxSize().testTag("yours.list"), state = listState) {
                item {
                    LazyRow(Modifier.fillMaxWidth().testTag("yours.chips")) {
                        items(CHIPS) { Text("chip $it", Modifier.width(120.dp).height(48.dp).testTag("yours.chip.$it")) }
                    }
                }
                items(ROWS) { Text("mine $it", Modifier.fillMaxWidth().height(72.dp).testTag("yours.row.$it")) }
            }
        },
        yoursSummary = { YoursSummary(count = ROWS, firstPublishedCode = null) },
    )

    private val content: @androidx.compose.runtime.Composable () -> Unit = {
        PuzzleBoxTheme {
            CompositionLocalProvider(LocalTabReselect provides reselect) {
                CommunityTabs(state, ACTIONS, friends, slot, onOpen = {}, onRemove = {}, onEnterCode = {}, Modifier.fillMaxSize(), sharePending = sharePending)
            }
        }
    }

    private fun show() {
        rule.setContent(content)
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    /** A finger swipe across most of the node's width: to the next page (left) or back (right). */
    private fun swipe(tag: String, left: Boolean) {
        rule.onNodeWithTag(tag).performTouchInput {
            down(center)
            moveBy(Offset(if (left) -width * 0.7f else width * 0.7f, 0f), delayMillis = 16)
            up()
        }
        rule.waitForIdle()
    }

    private fun isShown(tag: String) = runCatching { rule.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess

    private fun newRow(index: Int) = "community.row.${newCode(index).value}"

    private fun friendRow(index: Int) = "library.friends.row.${friendCode(index).value}"

    @Test
    fun `the filter chips show only while New today is the current tab`() {
        show()
        rule.onNodeWithTag(FILTER).assertIsDisplayed()
        val enterCodeAt = rule.onNodeWithTag(ENTER_CODE).getUnclippedBoundsInRoot().left

        tap(TAB_FRIENDS)
        rule.onNodeWithTag(FILTER).assertDoesNotExist()
        rule.onNodeWithTag(ENTER_CODE).assertIsDisplayed()
        assertEquals("the head must not shift when the chips leave", enterCodeAt, rule.onNodeWithTag(ENTER_CODE).getUnclippedBoundsInRoot().left)

        tap(TAB_YOURS)
        rule.onNodeWithTag(FILTER).assertDoesNotExist()

        tap(TAB_NEW)
        rule.onNodeWithTag(FILTER).assertIsDisplayed()
    }

    @Test
    fun `each tab shows only its own rows`() {
        show()

        assertTrue(isShown(newRow(0)))
        rule.onNodeWithTag(friendRow(0)).assertDoesNotExist()
        rule.onNodeWithTag("yours.row.0").assertDoesNotExist()
        // The tab names the list: no section head of the one-list layout is left.
        rule.onNodeWithTag("community.head").assertDoesNotExist()

        tap(TAB_FRIENDS)
        assertTrue(isShown(friendRow(0)))
        rule.onNodeWithTag(newRow(0)).assertDoesNotExist()
        rule.onNodeWithTag("yours.row.0").assertDoesNotExist()
        rule.onNodeWithText("From friends").assertDoesNotExist()

        tap(TAB_YOURS)
        assertTrue(isShown("yours.row.0"))
        rule.onNodeWithTag(newRow(0)).assertDoesNotExist()
        rule.onNodeWithTag(friendRow(0)).assertDoesNotExist()
    }

    @Test
    fun `the tabs count their rows`() {
        show()

        rule.onNodeWithTag("$TAB_NEW.count", useUnmergedTree = true).assertTextEquals(NEW_ROWS.toString())
        rule.onNodeWithTag("$TAB_FRIENDS.count", useUnmergedTree = true).assertTextEquals(FRIENDS.size.toString())
        rule.onNodeWithTag("$TAB_YOURS.count", useUnmergedTree = true).assertTextEquals(ROWS.toString())
    }

    @Test
    fun `New today has no count while it loads`() {
        state = LOADED.copy(phase = CommunityPhase.Loading)
        show()

        rule.onNodeWithTag(TAB_NEW).assertIsDisplayed()
        rule.onNodeWithTag("$TAB_NEW.count", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a bottom bar reselect scrolls only the current tab to its top`() {
        show()
        rule.onNodeWithTag(PAGE_NEW).performScrollToIndex(20)
        tap(TAB_FRIENDS)
        rule.onNodeWithTag(PAGE_FRIENDS).performScrollToIndex(8)
        rule.waitForIdle()
        assertFalse(isShown(friendRow(0)))

        assertTrue(reselect.tryEmit(Unit))
        rule.waitForIdle()

        assertTrue("Friends went back to its top", isShown(friendRow(0)))
        tap(TAB_NEW)
        assertTrue("New today stayed where it was", isShown(newRow(20)))
        assertFalse(isShown(newRow(0)))
    }

    @Test
    fun `a tap on the selected tab scrolls that tab to its top`() {
        show()
        rule.onNodeWithTag(PAGE_NEW).performScrollToIndex(20)
        rule.waitForIdle()
        assertFalse(isShown(newRow(0)))

        tap(TAB_NEW)

        assertTrue(isShown(newRow(0)))
        rule.onNodeWithTag(TAB_NEW).assertIsSelected()
    }

    @Test
    fun `the first swipe from New today to Friends keeps the New today scroll position`() {
        show()
        rule.onNodeWithTag(PAGE_NEW).performScrollToIndex(20)
        rule.waitForIdle()

        swipe(PAGER, left = true)
        rule.onNodeWithTag(TAB_FRIENDS).assertIsSelected()
        rule.onNodeWithTag(FILTER).assertDoesNotExist()
        assertTrue(isShown(friendRow(0)))

        swipe(PAGER, left = false)
        rule.onNodeWithTag(TAB_NEW).assertIsSelected()
        assertTrue("New today is where the swipe left it", isShown(newRow(20)))
        assertFalse(isShown(newRow(0)))
    }

    @Test
    fun `a sideways drag on a row scroller in Yours scrolls it and keeps the tab`() {
        show()
        tap(TAB_YOURS)
        assertTrue(isShown("yours.chip.0"))

        swipe("yours.chips", left = true)

        rule.onNodeWithTag(TAB_YOURS).assertIsSelected()
        assertTrue(isShown("yours.row.0"))
        assertFalse("the row scrolled under the finger", isShown("yours.chip.0"))
    }

    @Test
    fun `Make a puzzle on an empty New today selects Yours`() {
        state = LOADED.copy(phase = CommunityPhase.Loaded(emptyList()))
        show()

        rule.onNodeWithText("Make a puzzle").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(TAB_YOURS).assertIsSelected()
        assertTrue(isShown("yours.row.0"))
        rule.onNodeWithTag(FILTER).assertDoesNotExist()
    }

    @Test
    fun `Make a puzzle answers again after a drag back while it was moving to Yours`() {
        state = LOADED.copy(phase = CommunityPhase.Loaded(emptyList()))
        show()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Make a puzzle").performClick()
        // A few frames into the 300 ms move, the finger takes the pager and drags it back.
        rule.mainClock.advanceTimeBy(48)
        rule.onNodeWithTag(PAGER).performTouchInput {
            down(Offset(width * 0.1f, centerY))
            repeat(4) { moveBy(Offset(width * 0.2f, 0f), delayMillis = 16) }
            up()
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithTag(TAB_NEW).assertIsSelected()

        rule.onNodeWithText("Make a puzzle").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(TAB_YOURS).assertIsSelected()
        assertTrue(isShown("yours.row.0"))
    }

    @Test
    fun `offline the saved friend puzzles sit under the offline pill`() {
        state = LOADED.copy(phase = CommunityPhase.Offline)
        show()
        rule.onNodeWithTag("community.offline").assertIsDisplayed()
        rule.onNodeWithTag(OFFLINE_PILL).assertDoesNotExist()

        tap(TAB_FRIENDS)

        rule.onNodeWithTag(OFFLINE_PILL).assertIsDisplayed()
        assertTrue(isShown(friendRow(0)))
    }

    @Test
    fun `with no friend puzzles Friends offers Enter code once, in the head`() {
        friends = emptyList()
        show()

        tap(TAB_FRIENDS)

        rule.onAllNodesWithText("Enter code").assertCountEquals(1)
        rule.onNodeWithTag(ENTER_CODE).assertIsDisplayed()
        rule.onNodeWithTag("$TAB_FRIENDS.count", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the tab and each tab's scroll position survive a restore`() {
        val restore = StateRestorationTester(rule)
        restore.setContent(content)
        rule.onNodeWithTag(PAGE_NEW).performScrollToIndex(20)
        tap(TAB_FRIENDS)
        rule.onNodeWithTag(PAGE_FRIENDS).performScrollToIndex(8)
        rule.waitForIdle()

        restore.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag(TAB_FRIENDS).assertIsSelected()
        rule.onNodeWithTag(FILTER).assertDoesNotExist()
        assertTrue(isShown(friendRow(8)))
        tap(TAB_NEW)
        assertTrue(isShown(newRow(20)))
    }

    @Test
    fun `a text shared before the tabs show starts them on Yours`() {
        sharePending = true
        show()

        rule.onNodeWithTag(TAB_YOURS).assertIsSelected()
        assertTrue(isShown("yours.list"))
    }

    @Test
    fun `a text shared while New today shows opens Yours once and leaves later tab choices alone`() {
        show()
        rule.onNodeWithTag(TAB_NEW).assertIsSelected()

        sharePending = true
        rule.waitForIdle()
        rule.onNodeWithTag(TAB_YOURS).assertIsSelected()

        sharePending = false
        tap(TAB_FRIENDS)
        rule.onNodeWithTag(TAB_FRIENDS).assertIsSelected()
        rule.waitForIdle()
        rule.onNodeWithTag(TAB_FRIENDS).assertIsSelected()
    }

    private companion object {
        const val TAB_NEW = "community.tab.new"
        const val TAB_FRIENDS = "community.tab.friends"
        const val TAB_YOURS = "community.tab.yours"
        const val PAGE_NEW = "community.page.new"
        const val PAGE_FRIENDS = "community.page.friends"
        const val PAGER = "underline.pager"
        const val FILTER = "community.filter.game"
        const val ENTER_CODE = "community.enterCode"
        const val OFFLINE_PILL = "library.offline"

        const val NEW_ROWS = 30
        const val ROWS = 40
        const val CHIPS = 20

        val ZIP = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)

        fun newCode(index: Int): ShareCode = checkNotNull(ShareCode.parse("AB%04d".format(index + 2000)))

        fun friendCode(index: Int): ShareCode = checkNotNull(ShareCode.parse("FR%04d".format(index + 2000)))

        val ACTIONS = CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {})

        val LOADED = CommunityState(
            phase = CommunityPhase.Loaded(
                List(NEW_ROWS) { CommunityRowModel(newCode(it), "Zip", ZIP, "New puzzle $it", author = "Maya", difficulty = Difficulty.Level.Hard, age = "2 h ago") },
            ),
        )

        val FRIENDS = List(12) {
            FriendCardModel(FriendPuzzleRow(friendCode(it), GameId("zip"), "Friend puzzle $it", "Jonas", FriendStatus.New), gameName = "Zip", brand = ZIP)
        }
    }
}

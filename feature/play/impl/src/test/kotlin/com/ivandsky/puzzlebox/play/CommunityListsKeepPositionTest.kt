package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
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
 * Ticket 1509: a scrolled Community list holds its place when its data lands again, even when a new puzzle joins at the
 * top. A list that follows its position instead of the row under the thumb jumps by itself once the finger is up.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class CommunityListsKeepPositionTest {

    @get:Rule
    val rule = createComposeRule()

    private var state by mutableStateOf(communityState(range = 0 until ROWS, age = "2 h ago"))
    private var friends by mutableStateOf(friendCards(range = 0 until ROWS))

    private val slot = CustomSlot(
        yours = { listState -> LazyColumn(Modifier.fillMaxSize(), state = listState) { items(ROWS) { Text("mine $it") } } },
        yoursSummary = { YoursSummary(count = ROWS, firstPublishedCode = null) },
    )

    private fun show() {
        rule.setContent {
            PuzzleBoxTheme {
                CommunityTabs(
                    state,
                    CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {}),
                    friends,
                    slot,
                    onOpen = {},
                    onRemove = {},
                    onEnterCode = {},
                    Modifier.fillMaxSize(),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun top(tag: String): Dp {
        rule.onNodeWithTag(tag).assertIsDisplayed()
        return rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
    }

    private fun isShown(tag: String) = runCatching { rule.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess

    @Test
    fun `New today keeps its row under the thumb when the list loads again and a puzzle joins at the top`() {
        show()
        rule.onNodeWithTag("community.page.new").performScrollToIndex(20)
        rule.waitForIdle()
        val before = top(newRow(20))

        state = communityState(range = -3 until ROWS, age = "3 h ago")
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)

        assertTrue("the row under the thumb is still on screen", isShown(newRow(20)))
        assertFalse("the list did not return to its top", isShown(newRow(0)))
        assertEquals(before.value, top(newRow(20)).value, 0.5f)
    }

    @Test
    fun `Friends keeps its row under the thumb when its list loads again and a puzzle joins at the top`() {
        show()
        rule.onNodeWithTag("community.tab.friends").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("community.page.friends").performScrollToIndex(8)
        rule.waitForIdle()
        val before = top(friendRow(8))

        friends = friendCards(range = -2 until ROWS)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)

        assertTrue("the row under the thumb is still on screen", isShown(friendRow(8)))
        assertFalse("the list did not return to its top", isShown(friendRow(0)))
        assertEquals(before.value, top(friendRow(8)).value, 0.5f)
    }

    private companion object {
        const val ROWS = 30
        val ZIP = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)

        fun newRow(index: Int) = "community.row.${code("AB", index).value}"

        fun friendRow(index: Int) = "library.friends.row.${code("FR", index).value}"

        /** [index] may be negative: a row that came in above the first one the list held. */
        fun code(prefix: String, index: Int): ShareCode = checkNotNull(ShareCode.parse("%s%04d".format(prefix, index + 2000)))

        fun communityState(range: IntRange, age: String) = CommunityState(
            phase = CommunityPhase.Loaded(
                range.map { CommunityRowModel(code("AB", it), "Zip", ZIP, "New puzzle $it", author = "Maya", difficulty = Difficulty.Level.Hard, age = age) },
            ),
        )

        fun friendCards(range: IntRange) = range.map {
            FriendCardModel(FriendPuzzleRow(code("FR", it), GameId("zip"), "Friend puzzle $it", "Jonas", FriendStatus.New), gameName = "Zip", brand = ZIP)
        }
    }
}

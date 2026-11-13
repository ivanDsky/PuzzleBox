package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Games tabs building blocks: the pill row, the count rule and the Friends body. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class GamesTabsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tabPillClickInvokesOnClickAndExposesSelectedState() {
        var clicks = 0
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                GamesTabs {
                    GamesTabPill("Duels", selected = true, onClick = { clicks++ }, tag = "tab.on", trailing = { Text("2") })
                    GamesTabPill("Friends", selected = false, onClick = {}, tag = "tab.off")
                }
            }
        }

        composeRule.onNodeWithTag("tab.on").assertIsSelected().performClick()
        composeRule.onNodeWithTag("tab.off").assertIsNotSelected()
        assertEquals(1, clicks)
    }

    @Test
    fun tabCountTextCapsAtNineAndHidesZero() {
        assertEquals(null, tabCountText(0))
        assertEquals("1", tabCountText(1))
        assertEquals("9", tabCountText(9))
        assertEquals("9+", tabCountText(10))
    }

    @Test
    fun friendsBodyShowsOneCardPerFriendAndOpensIt() {
        val opened = ArrayList<ShareCode>()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                FriendsTabBody(FriendPreviewCards.take(2), onOpen = { opened.add(it) }, onRemove = {}, onEnterCode = {})
            }
        }

        FriendPreviewCards.take(2).forEach {
            composeRule.onNodeWithTag("library.friends.row.${it.row.code.value}").assertExists()
        }
        composeRule.onNodeWithTag("library.friends.row.${FriendPreviewCards[1].row.code.value}").performClick()
        assertEquals(listOf(FriendPreviewCards[1].row.code), opened)
    }

    @Test
    fun emptyFriendsBodyOffersEnterCode() {
        var entered = 0
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                FriendsTabBody(emptyList(), onOpen = {}, onRemove = {}, onEnterCode = { entered++ })
            }
        }

        composeRule.onNodeWithTag("library.friends.enter_code").performClick()
        assertEquals(1, entered)
    }
}

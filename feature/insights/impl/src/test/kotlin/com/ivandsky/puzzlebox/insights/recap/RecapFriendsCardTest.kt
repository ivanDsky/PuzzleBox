package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.friendsLoaded
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.friendsSettling
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.friendsUnavailable
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Best game among friends page in each state it can arrive in, and the card joining a story that is already open. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class RecapFriendsCardTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(card: RecapCard) = rule.setContent {
        PuzzleBoxTheme { RecapCardPage(card, "November 2026", gameName, onWatchReplay = {}) }
    }

    @Test
    fun `a best game names the game and its points among friends`() {
        show(friendsLoaded)

        rule.onNodeWithTag("recap.friends").assertIsDisplayed()
        rule.onNodeWithText("Best game among friends").assertIsDisplayed()
        rule.onNodeWithTag("recap.friends.loaded").assertTextContains("Queens").assertTextContains("1,340 pts among friends")
    }

    @Test
    fun `a settling month says results settle on the 4th and shows no number`() {
        show(friendsSettling)

        rule.onNodeWithTag("recap.friends.settling").assertIsDisplayed().assertTextContains("Results settle on the 4th")
        rule.onNodeWithTag("recap.friends.game").assertDoesNotExist()
        rule.onNodeWithTag("recap.friends.points").assertDoesNotExist()
    }

    @Test
    fun `an unavailable read says it cannot load right now in the card's place`() {
        show(friendsUnavailable)

        rule.onNodeWithTag("recap.friends.unavailable").assertIsDisplayed().assertTextContains("Can't load right now")
        rule.onNodeWithTag("recap.friends.game").assertDoesNotExist()
    }

    @Test
    fun `the tablet list names each state in a line`() {
        rule.setContent {
            PuzzleBoxTheme {
                RecapFactList(
                    cards = listOf(friendsLoaded, friendsSettling, friendsUnavailable),
                    current = 0,
                    gameName = gameName,
                    onSelect = {},
                    onWatchReplay = {},
                )
            }
        }

        rule.onNodeWithTag("recap.friends.fact.game").assertTextContains("Queens").assertTextContains("1,340 pts")
        rule.onNodeWithTag("recap.fact.1").assertTextContains("Results settle on the 4th")
        rule.onNodeWithTag("recap.fact.2").assertTextContains("Can't load right now")
    }

    @Test
    fun `a friends card that joins an open story is added at the end and the page stays`() {
        var state by mutableStateOf<RecapUiState>(RecapFixtures.ready)
        rule.setContent {
            PuzzleBoxTheme {
                RecapScreen(state, "November 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {})
            }
        }
        rule.onNodeWithTag("recap.pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card 2 of 2")

        state = RecapFixtures.ready.copy(cards = RecapFixtures.ready.cards + friendsUnavailable)
        rule.waitForIdle()

        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card 2 of 3")
        rule.onNodeWithTag("recap.pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("recap.friends.unavailable").assertIsDisplayed()
    }
}

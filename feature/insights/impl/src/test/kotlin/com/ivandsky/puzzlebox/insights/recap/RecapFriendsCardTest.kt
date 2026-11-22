package com.ivandsky.puzzlebox.insights.recap

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.FriendsStanding
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import org.junit.Assert.assertEquals
import org.junit.Before
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

    @Before
    fun animationsOff() {
        // The page's entrance is final at once, and the waiting crowd's wobble never keeps the test from going idle.
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun show(card: RecapCard) = rule.setContent {
        PuzzleBoxTheme { RecapCardPage(card, "November 2026", gameName, onWatchReplay = {}) }
    }

    @Test
    fun `a best game names the game and its points among friends`() {
        show(RecapFixtures.friendsLoaded)

        rule.onNodeWithTag("recap.friends").assertIsDisplayed()
        rule.onNodeWithText("Best game among friends").assertIsDisplayed()
        rule.onNodeWithTag("recap.friends.loaded").assertTextContains("Queens").assertTextContains("1,340 pts among friends")
    }

    @Test
    fun `a read that is still out holds the page with a quiet line and no number`() {
        show(RecapFixtures.friendsReading)

        rule.onNodeWithTag("recap.friends.reading").assertIsDisplayed().assertTextContains("Checking how your friends did\u2026")
        rule.onNodeWithText("Best game among friends").assertIsDisplayed()
        rule.onNodeWithTag("recap.friends.points").assertDoesNotExist()
    }

    @Test
    fun `a settling month says results settle on the 4th and shows no number`() {
        show(RecapFixtures.friendsSettling)

        rule.onNodeWithTag("recap.friends.settling").assertIsDisplayed().assertTextContains("Settles on the 4th")
        rule.onNodeWithTag("recap.friends.game").assertDoesNotExist()
        rule.onNodeWithTag("recap.friends.points").assertDoesNotExist()
    }

    @Test
    fun `an unavailable read says it cannot load right now in the card's place`() {
        show(RecapFixtures.friendsUnavailable)

        rule.onNodeWithTag("recap.friends.unavailable").assertIsDisplayed().assertTextContains("Can't load right now")
        rule.onNodeWithTag("recap.friends.game").assertDoesNotExist()
    }

    @Test
    fun `a best game among a crowd names the game, the points and how many friends`() {
        show(FriendsFixtures.loaded)

        rule.onNodeWithTag("recap.friends.loaded")
            .assertTextContains("Zip").assertTextContains("412 pts among friends").assertTextContains("Among 9 friends")
    }

    @Test
    fun `a year's best game shows a daily average over its weeks and no friend count`() {
        show(FriendsFixtures.year)

        rule.onNodeWithTag("recap.friends.loaded").assertTextContains("212 pts a day among friends")
        rule.onNodeWithTag("recap.friends.weeks", useUnmergedTree = true).assertTextContains("Based on 14 weeks")
        rule.onNodeWithText("Among", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a settling month still counts the friends once the read has them`() {
        show(FriendsFixtures.settling)

        rule.onNodeWithTag("recap.friends.settling").assertTextContains("Settles on the 4th").assertTextContains("Among 9 friends")
    }

    @Test
    fun `a friend with no name shows a question mark instead of an empty or crashing avatar`() {
        val unnamed = RecapCard.Friends(FriendsStanding.Loaded(RecapFixtures.zip, 90.0, friendCount = 2, friendNames = listOf("  ", "Maya Rao")))
        show(unnamed)

        rule.onAllNodesWithText("?", useUnmergedTree = true).onFirst().assertExists()
        rule.onAllNodesWithText("MR", useUnmergedTree = true).onFirst().assertExists()
        assertEquals("?", initials(" "))
        assertEquals("MR", initials("maya rao"))
    }

    @Test
    fun `the tablet list names each state in a line`() {
        rule.setContent {
            PuzzleBoxTheme {
                RecapFactList(
                    cards = listOf(RecapFixtures.friendsLoaded, RecapFixtures.friendsSettling, RecapFixtures.friendsUnavailable, RecapFixtures.friendsReading),
                    current = 0,
                    gameName = gameName,
                    onSelect = {},
                    onWatchReplay = {},
                )
            }
        }

        rule.onNodeWithTag("recap.friends.fact.game").assertTextContains("Queens").assertTextContains("1,340 pts")
        rule.onNodeWithTag("recap.fact.1").assertTextContains("Settles on the 4th")
        rule.onNodeWithTag("recap.fact.2").assertTextContains("Can't load right now")
        rule.onNodeWithTag("recap.fact.3").assertTextContains("Checking how your friends did\u2026")
    }

    @Test
    fun `a friends card that settles in an open story keeps the strip and the page`() {
        val withReading = RecapFixtures.ready.copy(cards = listOf(RecapFixtures.solved, RecapFixtures.friendsReading, RecapFixtures.secondSolved))
        var state by mutableStateOf<RecapUiState>(withReading)
        rule.setContent {
            PuzzleBoxTheme {
                RecapScreen(state, "November 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {})
            }
        }
        rule.tapPastCover()
        rule.onNodeWithTag("recap.pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("recap.friends.reading").assertIsDisplayed()
        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card 2 of 3")

        state = withReading.copy(cards = listOf(RecapFixtures.solved, RecapFixtures.friendsLoaded, RecapFixtures.secondSolved))
        rule.waitForIdle()

        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card 2 of 3")
        rule.onNodeWithTag("recap.friends.loaded").assertIsDisplayed()
    }
}

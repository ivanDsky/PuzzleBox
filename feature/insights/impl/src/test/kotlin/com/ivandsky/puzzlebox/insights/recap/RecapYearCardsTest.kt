package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.friendsLoaded
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.friendsLoadedYear
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.march
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.solved
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.solvedImported
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What a yearly recap's own pages say: the month card, an imports-only year's coverage, and a year's friends line. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class RecapYearCardsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(card: RecapCard, title: String = "2026 so far") = rule.setContent {
        PuzzleBoxTheme { RecapCardPage(card, title, gameName, onWatchReplay = {}) }
    }

    @Test
    fun `a month card names the month, its count, its fastest solve and the game`() {
        show(march)

        rule.onNodeWithTag("recap.month.2026-03").assertIsDisplayed()
        rule.onNodeWithTag("recap.month.name").assertTextEquals("March")
        rule.onNodeWithTag("recap.month.fastest").assertTextContains("0:58")
        rule.onNodeWithTag("recap.month.game").assertTextContains("Zip")
    }

    @Test
    fun `a year under way is titled so far on its pages`() {
        show(march, title = "2026 so far")

        rule.onNodeWithText("2026 so far").assertIsDisplayed()
    }

    @Test
    fun `a year from imports says how many days it covers`() {
        show(solvedImported, title = "2025")

        rule.onNodeWithTag("recap.solved.imported").assertIsDisplayed().assertTextEquals("From 312 imported days")
    }

    @Test
    fun `a year with app solves has no imported line`() {
        show(solved)

        rule.onNodeWithTag("recap.solved.imported").assertDoesNotExist()
    }

    @Test
    fun `a year's best game says it is based on its kept weeks`() {
        show(friendsLoadedYear)

        rule.onNodeWithTag("recap.friends.loaded")
            .assertTextContains("Queens")
            .assertTextContains("212 pts a day among friends")
            .assertTextContains("Based on 6 weeks")
    }

    @Test
    fun `a month's best game states no weeks`() {
        show(friendsLoaded, title = "November 2026")

        rule.onNodeWithTag("recap.friends.loaded").assertTextContains("1,340 pts among friends")
        rule.onNodeWithText("Based on", substring = true).assertDoesNotExist()
    }
}

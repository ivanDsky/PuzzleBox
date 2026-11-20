package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.fastest
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Fastest page's game tabs: a tap jumps to a game, a swipe moves the highlight, and each tab is a real tap target. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class FastestGameTabsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(card: RecapCard.Fastest) {
        rule.setContent { PuzzleBoxTheme { FastestCardPage(card, "November 2026", gameName, onWatchReplay = {}) } }
        settle()
    }

    private fun settle() = rule.mainClock.advanceTimeBy(3_000)

    @Test
    fun `tapping the last game's tab shows its panel and highlights it, and a swipe then moves on from there`() {
        show(fastest)
        rule.onNodeWithTag("recap.fastest.tab.0").assertIsSelected()

        rule.onNodeWithTag("recap.fastest.tab.2").performClick()
        settle()

        rule.onNodeWithTag("recap.fastest.card.sudoku").assertExists()
        rule.onNodeWithTag("recap.fastest.tab.2").assertIsSelected()
        rule.onNodeWithTag("recap.fastest.tab.0").assertIsNotSelected()
        // Past the last game the swipe is the deck's: the highlight stays on the last tab.
        rule.onNodeWithTag("recap.fastest.games").performTouchInput { swipeLeft() }
        settle()
        rule.onNodeWithTag("recap.fastest.tab.2").assertIsSelected()
    }

    @Test
    fun `swiping to the next game moves the highlight to its tab`() {
        show(fastest)

        rule.onNodeWithTag("recap.fastest.games").performTouchInput { swipeLeft() }
        settle()

        rule.onNodeWithTag("recap.fastest.tab.1").assertIsSelected()
        rule.onNodeWithTag("recap.fastest.tab.0").assertIsNotSelected()
    }

    @Test
    fun `each tab is at least 48dp tall and reads its game and place`() {
        show(fastest)

        rule.onNodeWithTag("recap.fastest.tab.1").assertHeightIsAtLeast(48.dp).assertContentDescriptionEquals("Fastest solve for Queens, 2 of 3")
    }

    @Test
    fun `a single game shows one tab`() {
        show(RecapCard.Fastest(fastest.rows.take(1)))

        rule.onNodeWithTag("recap.fastest.tab.0").assertIsSelected()
        rule.onNodeWithTag("recap.fastest.tab.1").assertDoesNotExist()
    }
}

package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.ready
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The recap on a tablet: the story on the left, every fact as a list on the right that follows and drives the story. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class RecapTabletTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show() = rule.setContent {
        PuzzleBoxTheme { RecapScreen(ready, "November 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {}) }
    }

    @Test
    fun `shows the story beside the fact list, the first fact selected`() {
        show()

        rule.onNodeWithTag("recap.twoPane").assertIsDisplayed()
        rule.onNodeWithTag("recap.pager").assertIsDisplayed()
        rule.onNodeWithTag("recap.facts").assertIsDisplayed()
        rule.onNodeWithTag("recap.fact.0").assertIsSelected()
        rule.onNodeWithTag("recap.fact.1").assertIsNotSelected()
    }

    @Test
    fun `a fact opens its card in the story`() {
        show()

        rule.onNodeWithTag("recap.fact.1").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card 2 of 2")
        rule.onNodeWithTag("recap.fact.1").assertIsSelected()
        rule.onNodeWithTag("recap.fact.0").assertIsNotSelected()
    }
}

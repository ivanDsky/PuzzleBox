package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.fastest
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.queens
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.streak
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Fastest solve and Longest streak pages: what they say, and which rows open a replay. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class RecapNewCardsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<RecapCard.Fastest.Row>()

    private fun show(card: RecapCard) = rule.setContent {
        PuzzleBoxTheme { RecapCardPage(card, "November 2026", gameName, onWatchReplay = { opened += it }) }
    }

    @Test
    fun `fastest lists each game's time and day`() {
        show(fastest)

        rule.onNodeWithTag("recap.fastest").assertIsDisplayed()
        rule.onNodeWithTag("recap.fastest.game.zip").assertTextContains("Zip").assertTextContains("0:58").assertTextContains("Nov 3")
        rule.onNodeWithTag("recap.fastest.game.queens").assertTextContains("2:21").assertTextContains("Nov 12")
    }

    @Test
    fun `a row with a replay opens it`() {
        show(fastest)

        rule.onNodeWithTag("recap.fastest.game.queens").assertHasClickAction().performClick()

        assertEquals(listOf(fastest.rows[1]), opened)
        assertEquals(queens, opened.single().game)
        assertEquals(LocalDate.of(2026, 11, 12), opened.single().day)
    }

    @Test
    fun `an imported row says there is no replay and opens nothing`() {
        show(fastest)

        rule.onNodeWithTag("recap.fastest.game.sudoku").assertTextContains("No replay", substring = true).assertTextContains("5:12").assertHasNoClickAction()
        rule.onNode(hasTestTag("recap.fastest.game.sudoku").and(hasClickAction())).assertDoesNotExist()
        assertEquals(emptyList<RecapCard.Fastest.Row>(), opened)
    }

    @Test
    fun `streak shows its length, dates and freezes`() {
        show(streak)

        rule.onNodeWithTag("recap.streak").assertIsDisplayed()
        rule.onNodeWithTag("recap.streak.days").assertTextContains("17")
        rule.onNodeWithTag("recap.streak.range").assertTextContains("Nov 3 – Nov 19")
        rule.onNodeWithTag("recap.streak.freezes").assertTextContains("Freezes used").assertTextContains("2")
    }

    @Test
    fun `a one-day streak shows its day once`() {
        show(streak.copy(days = 1, from = LocalDate.of(2026, 11, 9), to = LocalDate.of(2026, 11, 9), freezesSpent = 0))

        rule.onNodeWithTag("recap.streak.range").assertTextContains("Nov 9")
        rule.onNodeWithTag("recap.streak.freezes").assertTextContains("0")
    }

    @Test
    fun `nothing on the streak page is a button`() {
        show(streak)

        rule.onNode(hasClickAction()).assertDoesNotExist()
    }
}

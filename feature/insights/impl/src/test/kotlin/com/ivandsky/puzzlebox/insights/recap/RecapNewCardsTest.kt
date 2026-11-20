package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.fastest
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.queens
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.streak
import androidx.compose.ui.unit.dp
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

    private fun show(card: RecapCard) {
        rule.setContent { PuzzleBoxTheme { RecapCardPage(card, "November 2026", gameName, onWatchReplay = { opened += it }) } }
        settle()
    }

    /** Lets a card's slam play out: its delays are not idling work, so the clock is moved by hand. */
    private fun settle() = rule.mainClock.advanceTimeBy(3_000)

    private fun swipeCard(left: Boolean = true) =
        rule.onNodeWithTag("recap.fastest.games").performTouchInput { if (left) swipeLeft() else swipeRight() }.also { settle() }

    @Test
    fun `fastest opens on the first game with its time, day and puzzle`() {
        show(fastest)

        rule.onNodeWithTag("recap.fastest").assertIsDisplayed()
        rule.onNodeWithContentDescription("0:58").assertExists()
        rule.onNodeWithTag("recap.fastest.meta").assertTextContains("Nov 3 · Puzzle #212")
        rule.onNodeWithText("Zip").assertExists()
    }

    @Test
    fun `the replay pill opens that game's replay with its day and number`() {
        show(fastest)

        rule.onNodeWithTag("recap.fastest.replay").assertHasClickAction().performClick()

        assertEquals(listOf(fastest.rows[0]), opened)
        assertEquals(LocalDate.of(2026, 11, 3), opened.single().day)
        assertEquals(212, opened.single().puzzleNumber)
    }

    @Test
    fun `a swipe steps to the next game and the pill replays that game`() {
        show(fastest)

        swipeCard()
        rule.onNodeWithContentDescription("2:21").assertExists()
        rule.onNodeWithTag("recap.fastest.meta").assertTextContains("Nov 12 · Puzzle #190")
        rule.onNodeWithTag("recap.fastest.replay").performClick()

        assertEquals(listOf(fastest.rows[1]), opened)
        assertEquals(queens, opened.single().game)
    }

    @Test
    fun `an imported game says so and has no replay to open`() {
        show(fastest)

        swipeCard()
        swipeCard()

        rule.onNodeWithContentDescription("5:12").assertExists()
        rule.onNodeWithText("Imported from LinkedIn").assertExists()
        rule.onNodeWithTag("recap.fastest.replay").assertDoesNotExist()
        rule.onNode(hasTestTag("recap.fastest.card.sudoku").and(hasClickAction())).assertDoesNotExist()
        assertEquals(emptyList<RecapCard.Fastest.Row>(), opened)
    }

    @Test
    fun `a swipe past either end of the games keeps the card on its game`() {
        show(fastest)

        swipeCard(left = false)
        rule.onNodeWithContentDescription("0:58").assertExists()
        repeat(4) { swipeCard() }
        rule.onNodeWithContentDescription("5:12").assertExists()
    }

    @Test
    fun `a game with one solve says there is no average yet`() {
        show(fastest)

        swipeCard()
        swipeCard()
        rule.onNodeWithText("First solve — no average yet").assertExists()
    }

    @Test
    fun `the tablet fact lists each game's time and day`() {
        rule.setContent { PuzzleBoxTheme { FastestCardFact(fastest, gameName, onWatchReplay = { opened += it }) } }

        rule.onNodeWithTag("recap.fastest.game.zip").assertTextContains("Zip").assertTextContains("0:58").assertTextContains("Nov 3")
        rule.onNodeWithTag("recap.fastest.game.queens").assertTextContains("2:21").assertTextContains("Nov 12")
        rule.onNodeWithTag("recap.fastest.game.queens").assertHasClickAction().performClick()
        assertEquals(listOf(fastest.rows[1]), opened)
        rule.onNodeWithTag("recap.fastest.game.sudoku").assertTextContains("No replay", substring = true).assertHasNoClickAction()
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

    @Test
    fun `solved reads the settled total, each game's count and the stripe's numbers`() {
        show(RecapFixtures.solved)

        rule.onNodeWithTag("recap.solved.total").assertContentDescriptionEquals("42 puzzles solved")
        rule.onNodeWithTag("recap.solved.game.queens").assertContentDescriptionEquals("Queens, 15")
        rule.onNodeWithTag("recap.hintfree.count").assertContentDescriptionEquals("36 without a hint")
        rule.onNodeWithTag("recap.hintfree.share").assertTextContains("86% of solves")
        rule.onNodeWithTag("recap.hintfree.hinted").assertTextContains("6 with a hint")
        rule.onNodeWithTag("recap.hintfree.run").assertTextContains("Longest run: 9 in a row")
    }

    @Test
    fun `solved with every solve hint-free is the full share and has no hinted segment`() {
        show(RecapCard.Solved(1, listOf(RecapCard.Solved.PerGame(RecapFixtures.zip, 1)), hintFree = 1, longestHintFreeRun = 1))

        rule.onNodeWithTag("recap.hintfree.count").assertContentDescriptionEquals("1 without a hint")
        rule.onNodeWithTag("recap.hintfree.share").assertTextContains("100% of solves")
        rule.onNodeWithTag("recap.hintfree.hinted").assertDoesNotExist()
    }

    @Test
    fun `solved with no hint-free solve shows no stripe`() {
        show(RecapFixtures.solved.copy(hintFree = 0, longestHintFreeRun = null))

        rule.onNodeWithTag("recap.solved.total").assertIsDisplayed()
        rule.onNodeWithTag("recap.hintfree.count").assertDoesNotExist()
        rule.onNodeWithTag("recap.hintfree.share").assertDoesNotExist()
    }

    @Test
    fun `solved with no single run to name leaves out the longest run and keeps the hinted count`() {
        show(RecapFixtures.solved.copy(longestHintFreeRun = null))

        rule.onNodeWithTag("recap.hintfree.run").assertDoesNotExist()
        rule.onNodeWithTag("recap.hintfree.hinted").assertTextContains("6 with a hint")
    }

    @Test
    fun `solved says how many imported days a year covers`() {
        show(RecapFixtures.solvedImported)

        rule.onNodeWithTag("recap.solved.imported").assertTextContains("From 312 imported days")
    }

    @Test
    fun `moments shows the count and a row for each kind that came up`() {
        show(RecapFixtures.moments)

        rule.onNodeWithTag("recap.moments").assertIsDisplayed()
        rule.onNodeWithTag("recap.moments.count").assertTextContains("23")
        rule.onNodeWithTag("recap.moments.personalBest").assertTextContains("Personal bests").assertTextContains("5")
        rule.onNodeWithTag("recap.moments.firstSubX").assertTextContains("2")
        rule.onNodeWithTag("recap.moments.fullSet").assertTextContains("14")
        rule.onNodeWithTag("recap.moments.streak").assertTextContains("7 days · 30 days")
    }

    @Test
    fun `moments leaves out the kinds that did not come up`() {
        show(RecapCard.Moments(personalBests = 0, firstSubX = 0, fullSets = 1, streakMilestones = emptyList()))

        rule.onNodeWithTag("recap.moments.count").assertContentDescriptionEquals("1 moment earned")
        rule.onNodeWithTag("recap.moments.fullSet").assertIsDisplayed()
        rule.onNodeWithTag("recap.moments.personalBest").assertDoesNotExist()
        rule.onNodeWithTag("recap.moments.firstSubX").assertDoesNotExist()
        rule.onNodeWithTag("recap.moments.streak").assertDoesNotExist()
    }

    @Test
    fun `total time shows the time, the puzzles and the average per puzzle`() {
        show(RecapFixtures.totalTime)

        rule.onNodeWithTag("recap.totaltime").assertIsDisplayed()
        rule.onNodeWithTag("recap.totaltime.time").assertTextContains("3h 12m")
        rule.onNodeWithTag("recap.totaltime.solved").assertTextContains("42")
        rule.onNodeWithTag("recap.totaltime.average").assertTextContains("4:34")
    }

    @Test
    fun `a fact row is one item for a screen reader and no row is smaller than a touch target`() {
        show(RecapFixtures.totalTime)

        rule.onNodeWithTag("recap.totaltime.average").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("recap.totaltime.average").assertTextContains("Average per puzzle")
    }

    @Test
    fun `nothing on the solved page is a button`() {
        show(RecapFixtures.solved)
        rule.onNode(hasClickAction()).assertDoesNotExist()
    }
}

package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.friendsLoaded
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.friendsLoadedYear
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.march
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.solved
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.solvedImported
import org.junit.Assert.assertEquals
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

    private fun show(
        card: RecapCard,
        title: String = "2026 so far",
        place: MonthPlace? = null,
        name: String? = null,
        onShare: () -> Unit = {},
    ) = rule.setContent {
        PuzzleBoxTheme { RecapCardPage(card, title, gameName, onWatchReplay = {}, monthPlace = place, displayName = name, onShare = onShare) }
    }

    private val finale = YearFixtures.finale(months = 10, total = 355)

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

        rule.onNodeWithText("2026 SO FAR").assertIsDisplayed()
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

    @Test
    fun `a middle month names the next month and the last month names the finale`() {
        val cards = listOf(RecapFixtures.march, RecapFixtures.april)
        show(RecapFixtures.march, place = monthPlaceOf(cards, 0))
        rule.onNodeWithTag("recap.foot").assertTextEquals("Next: April")
        rule.onNodeWithTag("recap.month.index").assertTextEquals("MONTH 1 OF 2")
    }

    @Test
    fun `the last month's foot reads Next Year finale and counts the month cards`() {
        val cards = listOf(RecapFixtures.solved, RecapFixtures.march, RecapFixtures.april, finale)
        show(RecapFixtures.april, place = monthPlaceOf(cards, 2))

        rule.onNodeWithTag("recap.foot").assertTextEquals("Next: Year finale")
        rule.onNodeWithTag("recap.month.index").assertTextEquals("MONTH 2 OF 2")
    }

    @Test
    fun `a month with no solve shows an en dash on the finale`() {
        show(finale, name = "Ivan")

        rule.onNodeWithTag("recap.finale.tile.11").assertTextContains("–")
        rule.onNodeWithTag("recap.finale.tile.10").assertTextContains("96")
    }

    @Test
    fun `the finale reads its best month, fastest solve and most played game`() {
        show(finale, name = "Ivan")

        rule.onNodeWithTag("recap.finale.chip.best").assertTextContains("October").assertTextContains("96 puzzles")
        rule.onNodeWithTag("recap.finale.chip.fastest").assertTextContains("0:19").assertTextContains("Zip · Aug")
        rule.onNodeWithTag("recap.finale.chip.most").assertTextContains("Queens").assertTextContains("196 puzzles")
    }

    @Test
    fun `the finale note carries the name, and drops it when there is none`() {
        show(finale, name = "Ivan")
        rule.onNodeWithTag("recap.finale.note").assertTextEquals("The image shows your numbers and your name, Ivan, only.")
    }

    @Test
    fun `the finale note has no name clause for a blank name`() {
        show(finale, name = " ")
        rule.onNodeWithTag("recap.finale.note").assertTextEquals("The image shows your numbers only.")
    }

    @Test
    fun `the finale share button asks to move on to the share page`() {
        var shared = 0
        show(finale, name = "Ivan", onShare = { shared++ })

        rule.onNodeWithTag("recap.finale.share").performClick()

        assertEquals(1, shared)
    }
}

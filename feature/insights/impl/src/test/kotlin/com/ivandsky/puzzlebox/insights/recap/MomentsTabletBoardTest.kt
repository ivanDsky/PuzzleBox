package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Moments card on the golden tablet, in the states its goldens show: the board ends above the card's bottom padding,
 * every sticker sits inside the board's padding, the legend stays on the card, and nothing on the card scrolls.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class MomentsTabletBoardTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // Catches the reported overflow on a tablet: a year's fourth row cut off at the card's bottom edge.
    @Test
    fun `a year's stickers stay on the board and the board on the card`() = assertBoardFits(MomentsFixtures.year, year = true)

    @Test
    fun `the default board fits the card`() = assertBoardFits(MomentsFixtures.default)

    @Test
    fun `the longest board fits the card with a sticker peeled`() = assertBoardFits(MomentsFixtures.longest, peel = 11)

    @Test
    fun `the peeled board fits the card`() = assertBoardFits(MomentsFixtures.default, peel = 4)

    private fun assertBoardFits(card: RecapCard.Moments, peel: Int? = null, year: Boolean = false) {
        val story = if (year) MomentsFixtures.story(card, RecapFixtures.year2025, "2025") else MomentsFixtures.story(card)
        rule.setContent { PuzzleBoxTheme { RecapScreen(story, story.title, FastestFixtures.gameName, onPage = {}, onWatchReplay = {}, onClose = {}) } }
        rule.mainClock.advanceTimeBy(SettleMs)
        if (peel != null) {
            rule.onNodeWithTag("recap.moments.sticker.$peel").performClick()
            rule.mainClock.advanceTimeBy(PeelMs)
        }

        val page = rule.onNodeWithTag("recap.moments").getBoundsInRoot()
        val sheet = rule.onNodeWithTag("recap.moments.sheet").getBoundsInRoot()
        assertTrue("board $sheet past the card's padding $page", sheet.bottom <= page.bottom - CardPad + Tolerance)
        for (i in card.earned.indices) {
            val sticker = rule.onNodeWithTag("recap.moments.sticker.$i").getBoundsInRoot()
            assertTrue(
                "sticker $i $sticker outside the board's padding $sheet",
                sticker.left >= sheet.left + SheetPad - Tolerance && sticker.right <= sheet.right - SheetPad + Tolerance &&
                    sticker.top >= sheet.top + SheetPad - Tolerance && sticker.bottom <= sheet.bottom - SheetPad + Tolerance,
            )
        }
        val facts = rule.onNode(hasTestTag("recap.moments.facts") and hasAnyAncestor(hasTestTag("recap.moments"))).getBoundsInRoot()
        assertTrue("legend $facts off the card $page", facts.bottom <= page.bottom - CardPad + Tolerance)
        rule.onAllNodes(hasScrollAction() and hasAnyAncestor(hasTestTag("recap.moments"))).assertCountEquals(0)
    }
}

private const val SettleMs = 3_000L
private const val PeelMs = 600L

/** The card's padding, the sheet's least padding, and the rounding allowed when comparing edges. */
private val CardPad = 16.dp
private val SheetPad = 12.dp
private val Tolerance = 1.dp

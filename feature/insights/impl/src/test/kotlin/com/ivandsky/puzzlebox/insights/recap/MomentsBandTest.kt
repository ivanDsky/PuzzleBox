package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Moments card gives the room under its date label to the sticker board: the hero count takes no more height than
 * its line, so the board starts higher and a crowded year's stickers come out larger (before: 39dp on the golden phone,
 * 49dp on the golden tablet) while every sticker still sits inside the board and the board above the card's bottom padding.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MomentsBandTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE) @Test
    fun `a year's stickers are larger on a phone`() = assertBandGivenToBoard(PhoneYearBefore)

    @Config(qualifiers = GOLDEN_TABLET) @Test
    fun `a year's stickers are larger on a tablet`() = assertBandGivenToBoard(TabletYearBefore)

    private fun assertBandGivenToBoard(before: Float) {
        val card = MomentsFixtures.year
        val story = MomentsFixtures.story(card, RecapFixtures.year2025, "2025")
        rule.setContent { PuzzleBoxTheme { RecapScreen(story, story.title, FastestFixtures.gameName, onPage = {}, onWatchReplay = {}, onClose = {}) } }
        rule.mainClock.advanceTimeBy(SettleMs)
        val page = rule.onNodeWithTag("recap.moments").getBoundsInRoot()
        val sheet = rule.onNodeWithTag("recap.moments.sheet").getBoundsInRoot()
        assertTrue("board $sheet starts ${sheet.top - page.top} under the card's top, the hero band is still there", sheet.top - page.top <= BoardStartMax)
        assertTrue("board $sheet past the card's padding $page", sheet.bottom <= page.bottom - CardPad + Tolerance)
        var smallest = Float.MAX_VALUE
        for (i in card.earned.indices) {
            val sticker = rule.onNodeWithTag("recap.moments.sticker.$i").getBoundsInRoot()
            smallest = minOf(smallest, sticker.width.value)
            assertTrue(
                "sticker $i $sticker outside the board's padding $sheet",
                sticker.left >= sheet.left + SheetPad - Tolerance && sticker.right <= sheet.right - SheetPad + Tolerance &&
                    sticker.top >= sheet.top + SheetPad - Tolerance && sticker.bottom <= sheet.bottom - SheetPad + Tolerance,
            )
        }
        assertTrue("stickers ${smallest}dp are not larger than before (${before}dp)", smallest >= before + Growth)
    }
}

private const val SettleMs = 3_000L
private const val PhoneYearBefore = 39.2f
private const val TabletYearBefore = 49f

/** At least this much larger, in dp, so a rounding change is not mistaken for the band given away. */
private const val Growth = 4f

/** The farthest under the card's top, in dp, the board may start: its date label and the hero count's own line. */
private val BoardStartMax = 150.dp
private val CardPad = 16.dp
private val SheetPad = 12.dp
private val Tolerance = 1.dp

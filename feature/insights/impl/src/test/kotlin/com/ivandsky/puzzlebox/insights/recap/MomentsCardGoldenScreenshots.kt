package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Moments card in the four contract states (default, empty, longest, peeled), and a year's 32 stickers, on phone and
 * tablet, light and dark, each the sheet as it rests after the stickers are placed. The peeled frames tap a sticker as a
 * user would. Goldens in `snapshots/recap/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MomentsCardGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `moments default phone light`() = capture("default", "phone", false, MomentsFixtures.default)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `moments default phone dark`() = capture("default", "phone", true, MomentsFixtures.default)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `moments default tablet light`() = capture("default", "tablet", false, MomentsFixtures.default)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `moments default tablet dark`() = capture("default", "tablet", true, MomentsFixtures.default)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `moments empty phone light`() = capture("empty", "phone", false, MomentsFixtures.single)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `moments empty phone dark`() = capture("empty", "phone", true, MomentsFixtures.single)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `moments empty tablet light`() = capture("empty", "tablet", false, MomentsFixtures.single)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `moments empty tablet dark`() = capture("empty", "tablet", true, MomentsFixtures.single)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `moments longest phone light`() = capture("longest", "phone", false, MomentsFixtures.longest, peel = 11)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `moments longest phone dark`() = capture("longest", "phone", true, MomentsFixtures.longest, peel = 11)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `moments longest tablet light`() = capture("longest", "tablet", false, MomentsFixtures.longest, peel = 11)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `moments longest tablet dark`() = capture("longest", "tablet", true, MomentsFixtures.longest, peel = 11)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `moments peeled phone light`() = capture("peeled", "phone", false, MomentsFixtures.default, peel = 4)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `moments peeled phone dark`() = capture("peeled", "phone", true, MomentsFixtures.default, peel = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `moments peeled tablet light`() = capture("peeled", "tablet", false, MomentsFixtures.default, peel = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `moments peeled tablet dark`() = capture("peeled", "tablet", true, MomentsFixtures.default, peel = 4)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `moments year phone light`() = capture("year", "phone", false, MomentsFixtures.year, year = true)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `moments year phone dark`() = capture("year", "phone", true, MomentsFixtures.year, year = true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `moments year tablet light`() = capture("year", "tablet", false, MomentsFixtures.year, year = true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `moments year tablet dark`() = capture("year", "tablet", true, MomentsFixtures.year, year = true)

    private fun capture(state: String, device: String, dark: Boolean, card: RecapCard.Moments, peel: Int? = null, year: Boolean = false) {
        val story = if (year) MomentsFixtures.story(card, RecapFixtures.year2025, "2025") else MomentsFixtures.story(card)
        rule.showGolden(dark = dark) {
            RecapScreen(story, story.title, FastestFixtures.gameName, onPage = {}, onWatchReplay = {}, onClose = {})
        }
        if (peel != null) {
            rule.onNodeWithTag("recap.moments.sticker.$peel").performClick()
            rule.mainClock.advanceTimeBy(PEEL_MS)
        }
        rule.settleAndCapture("recap", "moments $state", device, dark)
    }
}

private const val PEEL_MS = 600L

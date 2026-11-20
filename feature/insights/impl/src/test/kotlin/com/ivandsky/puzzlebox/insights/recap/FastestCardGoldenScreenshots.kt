package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
 * Fastest card in the six contract states on phone and tablet, light and dark (motion contract frames, each the card as
 * it rests after the slam). The phone shows the game the contract shows, reached by swiping the card as a user would.
 * Goldens in `snapshots/recap/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FastestCardGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest default phone light`() = capture("default", "phone", false, FastestFixtures.default)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest default phone dark`() = capture("default", "phone", true, FastestFixtures.default)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest default tablet light`() = capture("default", "tablet", false, FastestFixtures.default)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest default tablet dark`() = capture("default", "tablet", true, FastestFixtures.default)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest imported phone light`() = capture("imported", "phone", false, FastestFixtures.default, swipes = 3)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest imported phone dark`() = capture("imported", "phone", true, FastestFixtures.default, swipes = 3)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest imported tablet light`() = capture("imported", "tablet", false, FastestFixtures.default, swipes = 3)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest imported tablet dark`() = capture("imported", "tablet", true, FastestFixtures.default, swipes = 3)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest no average phone light`() = capture("no average", "phone", false, FastestFixtures.noAverage, swipes = 4)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest no average phone dark`() = capture("no average", "phone", true, FastestFixtures.noAverage, swipes = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest no average tablet light`() = capture("no average", "tablet", false, FastestFixtures.noAverage, swipes = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest no average tablet dark`() = capture("no average", "tablet", true, FastestFixtures.noAverage, swipes = 4)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest last game phone light`() = capture("last game", "phone", false, FastestFixtures.default, swipes = 4)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest last game phone dark`() = capture("last game", "phone", true, FastestFixtures.default, swipes = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest last game tablet light`() = capture("last game", "tablet", false, FastestFixtures.default, swipes = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest last game tablet dark`() = capture("last game", "tablet", true, FastestFixtures.default, swipes = 4)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest single game phone light`() = capture("single game", "phone", false, FastestFixtures.singleGame)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest single game phone dark`() = capture("single game", "phone", true, FastestFixtures.singleGame)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest single game tablet light`() = capture("single game", "tablet", false, FastestFixtures.singleGame)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest single game tablet dark`() = capture("single game", "tablet", true, FastestFixtures.singleGame)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest longest phone light`() = capture("longest", "phone", false, FastestFixtures.longest, swipes = 4)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest longest phone dark`() = capture("longest", "phone", true, FastestFixtures.longest, swipes = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest longest tablet light`() = capture("longest", "tablet", false, FastestFixtures.longest, swipes = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest longest tablet dark`() = capture("longest", "tablet", true, FastestFixtures.longest, swipes = 4)

    private fun capture(state: String, device: String, dark: Boolean, card: RecapCard.Fastest, swipes: Int = 0) {
        rule.showGolden(dark = dark) {
            CompositionLocalProvider(LocalGameBrands provides FastestFixtures.brands) {
                RecapScreen(
                    FastestFixtures.story(card), "October 2026", FastestFixtures.gameName,
                    onPage = {}, onWatchReplay = {}, onClose = {},
                )
            }
        }
        if (device == "phone") repeat(swipes) {
            rule.onNodeWithTag("recap.fastest.games").performTouchInput { swipeLeft() }
            rule.mainClock.advanceTimeBy(STEP_MS)
        }
        rule.settleAndCapture("recap", "fastest $state", device, dark)
    }
}

private const val STEP_MS = 600L

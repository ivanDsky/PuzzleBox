package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
 * Fastest card pages in the contract states on phone, tablet and wide tablet, light and dark (motion contract frames,
 * each page as it rests after the slam): every game of the default month, its own page in its own accent, then the first
 * solve, the single game and the widest times. The story opens on the game's page. Goldens in `snapshots/recap/`.
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
    @Config(qualifiers = GOLDEN_TABLET_WIDE) @Test fun `fastest default tablet-wide light`() = capture("default", "tablet-wide", false, FastestFixtures.default)
    @Config(qualifiers = GOLDEN_TABLET_WIDE) @Test fun `fastest default tablet-wide dark`() = capture("default", "tablet-wide", true, FastestFixtures.default)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest tango phone light`() = capture("tango", "phone", false, FastestFixtures.default, game = 1)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest tango phone dark`() = capture("tango", "phone", true, FastestFixtures.default, game = 1)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest tango tablet light`() = capture("tango", "tablet", false, FastestFixtures.default, game = 1)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest tango tablet dark`() = capture("tango", "tablet", true, FastestFixtures.default, game = 1)
    @Config(qualifiers = GOLDEN_TABLET_WIDE) @Test fun `fastest tango tablet-wide light`() = capture("tango", "tablet-wide", false, FastestFixtures.default, game = 1)
    @Config(qualifiers = GOLDEN_TABLET_WIDE) @Test fun `fastest tango tablet-wide dark`() = capture("tango", "tablet-wide", true, FastestFixtures.default, game = 1)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest zip phone light`() = capture("zip", "phone", false, FastestFixtures.default, game = 2)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest zip phone dark`() = capture("zip", "phone", true, FastestFixtures.default, game = 2)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest zip tablet light`() = capture("zip", "tablet", false, FastestFixtures.default, game = 2)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest zip tablet dark`() = capture("zip", "tablet", true, FastestFixtures.default, game = 2)
    @Config(qualifiers = GOLDEN_TABLET_WIDE) @Test fun `fastest zip tablet-wide light`() = capture("zip", "tablet-wide", false, FastestFixtures.default, game = 2)
    @Config(qualifiers = GOLDEN_TABLET_WIDE) @Test fun `fastest zip tablet-wide dark`() = capture("zip", "tablet-wide", true, FastestFixtures.default, game = 2)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest imported phone light`() = capture("imported", "phone", false, FastestFixtures.default, game = 3)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest imported phone dark`() = capture("imported", "phone", true, FastestFixtures.default, game = 3)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest imported tablet light`() = capture("imported", "tablet", false, FastestFixtures.default, game = 3)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest imported tablet dark`() = capture("imported", "tablet", true, FastestFixtures.default, game = 3)
    @Config(qualifiers = GOLDEN_TABLET_WIDE) @Test fun `fastest imported tablet-wide light`() = capture("imported", "tablet-wide", false, FastestFixtures.default, game = 3)
    @Config(qualifiers = GOLDEN_TABLET_WIDE) @Test fun `fastest imported tablet-wide dark`() = capture("imported", "tablet-wide", true, FastestFixtures.default, game = 3)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest last game phone light`() = capture("last game", "phone", false, FastestFixtures.default, game = 4)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest last game phone dark`() = capture("last game", "phone", true, FastestFixtures.default, game = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest last game tablet light`() = capture("last game", "tablet", false, FastestFixtures.default, game = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest last game tablet dark`() = capture("last game", "tablet", true, FastestFixtures.default, game = 4)
    @Config(qualifiers = GOLDEN_TABLET_WIDE) @Test fun `fastest last game tablet-wide light`() = capture("last game", "tablet-wide", false, FastestFixtures.default, game = 4)
    @Config(qualifiers = GOLDEN_TABLET_WIDE) @Test fun `fastest last game tablet-wide dark`() = capture("last game", "tablet-wide", true, FastestFixtures.default, game = 4)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest no average phone light`() = capture("no average", "phone", false, FastestFixtures.noAverage, game = 4)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest no average phone dark`() = capture("no average", "phone", true, FastestFixtures.noAverage, game = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest no average tablet light`() = capture("no average", "tablet", false, FastestFixtures.noAverage, game = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest no average tablet dark`() = capture("no average", "tablet", true, FastestFixtures.noAverage, game = 4)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest single game phone light`() = capture("single game", "phone", false, FastestFixtures.singleGame)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest single game phone dark`() = capture("single game", "phone", true, FastestFixtures.singleGame)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest single game tablet light`() = capture("single game", "tablet", false, FastestFixtures.singleGame)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest single game tablet dark`() = capture("single game", "tablet", true, FastestFixtures.singleGame)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest longest phone light`() = capture("longest", "phone", false, FastestFixtures.longest, game = 4)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `fastest longest phone dark`() = capture("longest", "phone", true, FastestFixtures.longest, game = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest longest tablet light`() = capture("longest", "tablet", false, FastestFixtures.longest, game = 4)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `fastest longest tablet dark`() = capture("longest", "tablet", true, FastestFixtures.longest, game = 4)

    /** The story opened on [card]'s page for row [game] (the Fastest card is the story's second card). */
    private fun capture(state: String, device: String, dark: Boolean, card: RecapCard.Fastest, game: Int = 0) {
        rule.showGolden(dark = dark) {
            CompositionLocalProvider(LocalGameBrands provides FastestFixtures.brands) {
                RecapScreen(
                    FastestFixtures.story(card).copy(initialPage = 1 + game), "October 2026", FastestFixtures.gameName,
                    onPage = {}, onWatchReplay = {}, onClose = {},
                )
            }
        }
        rule.settleAndCapture("recap", "fastest $state", device, dark)
    }
}

/** A wide landscape tablet (Galaxy Tab S9 Ultra): the panel keeps the reference proportions when stretched. */
private const val GOLDEN_TABLET_WIDE = "w1680dp-h1050dp-land-mdpi"

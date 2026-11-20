package com.ivandsky.puzzlebox.insights.share

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.LocalGameBrands
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures
import com.ivandsky.puzzlebox.insights.recap.SolvedFixtures
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The share image itself, 1080 by 1920 px at 1 px per dp: a month and a year still under way (A22), light and dark, and
 * every style's hand of games for a month and a year with five games, two and one. Goldens in `snapshots/share/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = IMAGE_DEVICE)
class RecapShareImageGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val cards = listOf(RecapFixtures.solved, RecapFixtures.totalTime, RecapFixtures.streak, RecapFixtures.moments)

    @Test fun `month light`() = capture("month", "November 2026", "Ada", dark = false)

    @Test fun `month dark`() = capture("month", "November 2026", "Ada", dark = true)

    @Test fun `year so far light`() = capture("year-so-far", "2026 so far", "Ada", dark = false)

    @Test fun `year so far dark`() = capture("year-so-far", "2026 so far", "Ada", dark = true)

    @Test fun `signed out light`() = capture("signed-out", "November 2026", null, dark = false)

    @Test fun `few numbers dark`() = capture("few", "2026 so far", "A name that is much too long to fit on a single line of the image", dark = true, cards = listOf(RecapFixtures.solved, RecapFixtures.streak))

    @Test fun `classic light`() = capture("classic", "October 2026", "Ivan", dark = false, cards = ShareContract.default)

    @Test fun `classic dark`() = capture("classic", "October 2026", "Ivan", dark = true, cards = ShareContract.default)

    @Test fun `color light`() = capture("color", "October 2026", "Ivan", dark = false, cards = ShareContract.default, style = ShareStyle.Color)

    @Test fun `color dark`() = capture("color", "October 2026", "Ivan", dark = true, cards = ShareContract.default, style = ShareStyle.Color)

    @Test fun `night light`() = capture("night", "October 2026", "Ivan", dark = false, cards = ShareContract.default, style = ShareStyle.Night)

    @Test fun `night dark`() = capture("night", "October 2026", "Ivan", dark = true, cards = ShareContract.default, style = ShareStyle.Night)

    @Test fun `longest classic light`() = capture("longest-classic", "October 2026", ShareContract.LONG_NAME, dark = false, cards = ShareContract.longest)

    @Test fun `longest color dark`() = capture("longest-color", "October 2026", ShareContract.LONG_NAME, dark = true, cards = ShareContract.longest, style = ShareStyle.Color)

    @Test fun `longest night light`() = capture("longest-night", "October 2026", ShareContract.LONG_NAME, dark = false, cards = ShareContract.longest, style = ShareStyle.Night)

    @Test fun `empty classic light`() = capture("empty-classic", "October 2026", "Ivan", dark = false, cards = ShareContract.empty)

    @Test fun `empty classic dark`() = capture("empty-classic", "October 2026", "Ivan", dark = true, cards = ShareContract.empty)

    @Test fun `empty color light`() = capture("empty-color", "October 2026", "Ivan", dark = false, cards = ShareContract.empty, style = ShareStyle.Color)

    @Test fun `empty night light`() = capture("empty-night", "October 2026", "Ivan", dark = false, cards = ShareContract.empty, style = ShareStyle.Night)

    @Test fun `two games classic light`() = capture("two-games-classic", "October 2026", "Ivan", dark = false, cards = ShareContract.twoGames)

    @Test fun `two games classic dark`() = capture("two-games-classic", "October 2026", "Ivan", dark = true, cards = ShareContract.twoGames)

    @Test fun `two games color light`() = capture("two-games-color", "October 2026", "Ivan", dark = false, cards = ShareContract.twoGames, style = ShareStyle.Color)

    @Test fun `two games night dark`() = capture("two-games-night", "October 2026", "Ivan", dark = true, cards = ShareContract.twoGames, style = ShareStyle.Night)

    @Test fun `year classic light`() = capture("year-classic", "2026", "Ivan", dark = false, cards = ShareContract.year)

    @Test fun `year classic dark`() = capture("year-classic", "2026", "Ivan", dark = true, cards = ShareContract.year)

    @Test fun `year color light`() = capture("year-color", "2026", "Ivan", dark = false, cards = ShareContract.year, style = ShareStyle.Color)

    @Test fun `year night dark`() = capture("year-night", "2026", "Ivan", dark = true, cards = ShareContract.year, style = ShareStyle.Night)

    @Test fun `name hidden classic light`() = capture("name-hidden", "October 2026", null, dark = false, cards = ShareContract.default)

    private fun capture(
        name: String,
        title: String,
        displayName: String?,
        dark: Boolean,
        cards: List<RecapCard> = this.cards,
        style: ShareStyle = ShareStyle.Classic,
    ) {
        rule.showGolden(dark = dark) {
            CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) {
                RecapShareImage(RecapShareContent.of(title, displayName, cards, SolvedFixtures.gameName), style)
            }
        }
        rule.settleAndCapture("share", name, "image", dark)
    }
}

private const val IMAGE_DEVICE = "w1080dp-h1920dp-mdpi"

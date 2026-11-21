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

/** The year month card and the finale: six states on phone and tablet, light and dark. Goldens in `snapshots/recap/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class YearFinaleGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val year = YearFixtures.deck(YearFixtures.monthCards(), YearFixtures.finale())
    private val octoberAt = year.indexOfFirst { it is RecapCard.Month && it.month.monthValue == 10 }
    private val septemberAt = octoberAt - 1
    private val soFar = YearFixtures.deck(YearFixtures.monthCards(11), YearFixtures.finale(months = 11, total = 555))
    private val imported = YearFixtures.deck(YearFixtures.monthCards(), YearFixtures.finale(total = 615, importedDays = 412), imported = true)
    private val single = listOf(RecapFixtures.solved, RecapFixtures.fastest, RecapFixtures.streak, RecapFixtures.moments, RecapFixtures.friendsLoadedYear, RecapFixtures.totalTime, YearFixtures.oneMonth, YearFixtures.finale(months = 0, total = 1).let { it.copy(months = it.months.mapIndexed { i, t -> if (i == 9) t.copy(solved = 1, game = RecapFixtures.zip) else t }) })

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year month card phone light`() = month(year, octoberAt, "year-month-card", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year month card phone dark`() = month(year, octoberAt, "year-month-card", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year month card tablet light`() = month(year, octoberAt, "year-month-card", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year month card tablet dark`() = month(year, octoberAt, "year-month-card", "tablet", true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year month card longest phone light`() = month(year, septemberAt, "year-month-card-longest", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year month card longest phone dark`() = month(year, septemberAt, "year-month-card-longest", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year month card longest tablet light`() = month(year, septemberAt, "year-month-card-longest", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year month card longest tablet dark`() = month(year, septemberAt, "year-month-card-longest", "tablet", true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year finale phone light`() = finale(year, "2026", "year-finale", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year finale phone dark`() = finale(year, "2026", "year-finale", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year finale tablet light`() = finale(year, "2026", "year-finale", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year finale tablet dark`() = finale(year, "2026", "year-finale", "tablet", true)

    @Config(qualifiers = TABLET_WIDE) @Test fun `year finale tablet-wide light`() = finale(year, "2026", "year-finale", "tablet-wide", false)

    @Config(qualifiers = TABLET_SPLIT) @Test fun `year finale tablet-split light`() = finale(year, "2026", "year-finale", "tablet-split", false)
    @Config(qualifiers = TABLET_SPLIT) @Test fun `year finale tablet-split dark`() = finale(year, "2026", "year-finale", "tablet-split", true)
    @Config(qualifiers = TABLET_SPLIT) @Test fun `year finale imported tablet-split light`() = finale(imported, "2025", "year-finale-imported", "tablet-split", false)
    @Config(qualifiers = TABLET_SPLIT) @Test fun `year finale so far tablet-split light`() = finale(soFar, "2026 so far", "year-finale-so-far", "tablet-split", false)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year finale so far phone light`() = finale(soFar, "2026 so far", "year-finale-so-far", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year finale so far phone dark`() = finale(soFar, "2026 so far", "year-finale-so-far", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year finale so far tablet light`() = finale(soFar, "2026 so far", "year-finale-so-far", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year finale so far tablet dark`() = finale(soFar, "2026 so far", "year-finale-so-far", "tablet", true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year finale imported phone light`() = finale(imported, "2025", "year-finale-imported", "phone", false)
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year finale imported phone dark`() = finale(imported, "2025", "year-finale-imported", "phone", true)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year finale imported tablet light`() = finale(imported, "2025", "year-finale-imported", "tablet", false)
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year finale imported tablet dark`() = finale(imported, "2025", "year-finale-imported", "tablet", true)

    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year single month phone light`() = month(single, single.size - 2, "year-single-month", "phone", false, "2026 so far")
    @Config(qualifiers = GOLDEN_PHONE) @Test fun `year single month phone dark`() = month(single, single.size - 2, "year-single-month", "phone", true, "2026 so far")
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year single month tablet light`() = month(single, single.size - 2, "year-single-month", "tablet", false, "2026 so far")
    @Config(qualifiers = GOLDEN_TABLET) @Test fun `year single month tablet dark`() = month(single, single.size - 2, "year-single-month", "tablet", true, "2026 so far")

    private fun month(cards: List<RecapCard>, page: Int, name: String, device: String, dark: Boolean, title: String = "2026 so far") =
        capture(cards, page, title, name, device, dark)

    private fun finale(cards: List<RecapCard>, title: String, name: String, device: String, dark: Boolean) =
        capture(cards, cards.size - 1, title, name, device, dark)

    private fun capture(cards: List<RecapCard>, page: Int, title: String, name: String, device: String, dark: Boolean) {
        val state = readyYear(title, *cards.toTypedArray()).onCard(page)
        rule.showGolden(dark = dark) {
            CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) {
                RecapScreen(state, title, SolvedFixtures.gameName, onPage = {}, onWatchReplay = {}, onClose = {}, displayName = "Ivan")
            }
        }
        rule.settleAndCapture("recap", name, device, dark)
    }
}

/** The Galaxy Tab S9 Ultra, which the user reviews on: wider and taller than the 1280 x 800 tablet golden. */
private const val TABLET_WIDE = "w1680dp-h1050dp-land-mdpi"

/** A tablet in split screen: the pane is 560 x 1200dp, under 600dp wide, so it gets the phone layout on a tall page. */
private const val TABLET_SPLIT = "w560dp-h1200dp-mdpi"

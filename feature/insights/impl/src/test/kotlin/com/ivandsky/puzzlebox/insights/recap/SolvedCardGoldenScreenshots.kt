package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.ready
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Solved card renders: the contract states on phone, tablet and the widest tablet, light and dark, and the year pages. Goldens in `snapshots/recap/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SolvedCardGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved default phone light`() = state("default", SolvedFixtures.default, 7, "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved default phone dark`() = state("default", SolvedFixtures.default, 7, "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved default tablet light`() = state("default", SolvedFixtures.default, 7, "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved default tablet dark`() = state("default", SolvedFixtures.default, 7, "tablet", dark = true)

    @Config(qualifiers = WIDE_TABLET)
    @Test fun `solved default tablet-wide light`() = state("default", SolvedFixtures.default, 7, "tablet-wide", dark = false)

    @Config(qualifiers = WIDE_TABLET)
    @Test fun `solved default tablet-wide dark`() = state("default", SolvedFixtures.default, 7, "tablet-wide", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved empty phone light`() = state("empty", SolvedFixtures.empty, 5, "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved empty phone dark`() = state("empty", SolvedFixtures.empty, 5, "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved empty tablet light`() = state("empty", SolvedFixtures.empty, 5, "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved empty tablet dark`() = state("empty", SolvedFixtures.empty, 5, "tablet", dark = true)

    @Config(qualifiers = WIDE_TABLET)
    @Test fun `solved empty tablet-wide light`() = state("empty", SolvedFixtures.empty, 5, "tablet-wide", dark = false)

    @Config(qualifiers = WIDE_TABLET)
    @Test fun `solved empty tablet-wide dark`() = state("empty", SolvedFixtures.empty, 5, "tablet-wide", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved longest phone light`() = state("longest", SolvedFixtures.longest, 7, "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved longest phone dark`() = state("longest", SolvedFixtures.longest, 7, "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved longest tablet light`() = state("longest", SolvedFixtures.longest, 7, "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved longest tablet dark`() = state("longest", SolvedFixtures.longest, 7, "tablet", dark = true)

    @Config(qualifiers = WIDE_TABLET)
    @Test fun `solved longest tablet-wide light`() = state("longest", SolvedFixtures.longest, 7, "tablet-wide", dark = false)

    @Config(qualifiers = WIDE_TABLET)
    @Test fun `solved longest tablet-wide dark`() = state("longest", SolvedFixtures.longest, 7, "tablet-wide", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved no hint free phone light`() = state("no-hint-free", SolvedFixtures.noHintFree, 7, "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved no hint free phone dark`() = state("no-hint-free", SolvedFixtures.noHintFree, 7, "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved no hint free tablet light`() = state("no-hint-free", SolvedFixtures.noHintFree, 7, "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved no hint free tablet dark`() = state("no-hint-free", SolvedFixtures.noHintFree, 7, "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved single run phone light`() = state("single-run", SolvedFixtures.singleRun, 7, "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved single run phone dark`() = state("single-run", SolvedFixtures.singleRun, 7, "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved single run tablet light`() = state("single-run", SolvedFixtures.singleRun, 7, "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved single run tablet dark`() = state("single-run", SolvedFixtures.singleRun, 7, "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved year2025 phone light`() = year2025("phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved year2025 phone dark`() = year2025("phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved year2025 tablet light`() = year2025("tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved year2025 tablet dark`() = year2025("tablet", dark = true)

    @Config(qualifiers = WIDE_TABLET)
    @Test fun `solved year2025 tablet-wide light`() = year2025("tablet-wide", dark = false)

    @Config(qualifiers = WIDE_TABLET)
    @Test fun `solved year2025 tablet-wide dark`() = year2025("tablet-wide", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved huge phone light`() = state("huge", SolvedFixtures.huge, 7, "phone", dark = false, period = SolvedFixtures.hugePeriod)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `solved huge phone dark`() = state("huge", SolvedFixtures.huge, 7, "phone", dark = true, period = SolvedFixtures.hugePeriod)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved huge tablet light`() = state("huge", SolvedFixtures.huge, 7, "tablet", dark = false, period = SolvedFixtures.hugePeriod)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `solved huge tablet dark`() = state("huge", SolvedFixtures.huge, 7, "tablet", dark = true, period = SolvedFixtures.hugePeriod)

    @Config(qualifiers = WIDE_TABLET)
    @Test fun `solved huge tablet-wide light`() = state("huge", SolvedFixtures.huge, 7, "tablet-wide", dark = false, period = SolvedFixtures.hugePeriod)

    @Config(qualifiers = WIDE_TABLET)
    @Test fun `solved huge tablet-wide dark`() = state("huge", SolvedFixtures.huge, 7, "tablet-wide", dark = true, period = SolvedFixtures.hugePeriod)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `year so far totals phone light`() = capture("year-so-far", "phone", dark = false, state = readyYear("2026 so far", RecapFixtures.solved, RecapFixtures.march))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `year so far totals phone dark`() = capture("year-so-far", "phone", dark = true, state = readyYear("2026 so far", RecapFixtures.solved, RecapFixtures.march))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `year imported phone light`() = capture("year-imported", "phone", dark = false, state = readyYear("2025", RecapFixtures.solvedImported, RecapFixtures.march))

    /**
     * The contract state [card] over [period], first of a strip of [strip] segments (the cards after it only fill the
     * strip; the Share page is the last).
     */
    private fun state(name: String, card: RecapCard.Solved, strip: Int, device: String, dark: Boolean, period: String = "October 2026") {
        val others = listOf(RecapFixtures.fastest, RecapFixtures.streak, RecapFixtures.moments, RecapFixtures.friendsLoaded, RecapFixtures.totalTime)
        val cards = listOf<RecapCard>(card) + others.take(strip - 2)
        capture("solved-$name", device, dark, state = readyOf(*cards.toTypedArray()).copy(title = period), periodLabel = period, names = SolvedFixtures.gameName)
    }

    /** The user's 2025 as the first page of a year story. */
    private fun year2025(device: String, dark: Boolean) =
        capture("solved-year2025", device, dark, state = readyYear("2025", SolvedFixtures.year2025, RecapFixtures.march), periodLabel = "2025", names = SolvedFixtures.gameName)

    private fun capture(
        name: String,
        device: String,
        dark: Boolean,
        initialPage: Int = 0,
        state: RecapUiState = ready.copy(initialPage = initialPage),
        periodLabel: String = "November 2026",
        names: (GameId) -> String = gameName,
    ) {
        rule.showGolden(dark = dark) {
            CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) {
                RecapScreen(state, periodLabel, names, onPage = {}, onWatchReplay = {}, onClose = {})
            }
        }
        rule.settleAndCapture("recap", name, device, dark)
    }
}

/** The Galaxy Tab S9 Ultra in landscape, the widest screen the recap meets. */
private const val WIDE_TABLET = "w1680dp-h1050dp-land-mdpi"

package com.ivandsky.puzzlebox.insights.hub

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.recap.LocalGameBrands
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import com.ivandsky.puzzlebox.insights.domain.recap.RecapSummary
import com.ivandsky.puzzlebox.insights.domain.recap.SummarizedPeriod
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Insights hub on a phone and on the landscape tablet, light and dark, plus its waiting states. Goldens in `snapshots/hub/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class InsightsHubGoldenScreenshots {
    private companion object {
        const val GOLDEN_TABLET_WIDE = "w1680dp-h1050dp-land-mdpi"
    }


    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val alpha = GameId("alpha")
    private val beta = GameId("beta")

    private fun year(year: Int, solved: Int) =
        SummarizedPeriod(RecapPeriod.Year(year), RecapSummary(solved, listOf(alpha to solved), List(12) { 20 + it * (year % 7) }))

    private fun month(year: Int, month: Int, solved: Int) = SummarizedPeriod(
        RecapPeriod.Month(YearMonth.of(year, month)),
        RecapSummary(solved, listOf(alpha to solved * 2 / 3, beta to solved - solved * 2 / 3), emptyList()),
    )

    private val hub = InsightsHubUiState.Ready(
        listOf(year(2026, 625), year(2025, 615), month(2026, 11, 70), month(2026, 10, 61), month(2026, 9, 58), month(2026, 8, 55)),
        opened = setOf("2025", "2026-09", "2026-08"),
        yearSoFar = 2026,
    )

    /** Twenty-four months over two years, the grid scrolling. */
    private val longest = InsightsHubUiState.Ready(
        listOf(year(2026, 625), year(2025, 615)) +
            (11 downTo 1).map { month(2026, it, 40 + it * 3) } + (12 downTo 1).map { month(2025, it, 35 + it * 2) },
        opened = setOf("2025") + (1..9).map { "2026-%02d".format(it) } + (1..12).map { "2025-%02d".format(it) },
        yearSoFar = 2026,
    )

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `entry-points hub phone light`() = capture("entry-points__hub", "phone", dark = false, state = hub)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `entry-points hub phone dark`() = capture("entry-points__hub", "phone", dark = true, state = hub)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `entry-points hub tablet light`() = capture("entry-points__hub", "tablet", dark = false, state = hub)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `entry-points hub tablet dark`() = capture("entry-points__hub", "tablet", dark = true, state = hub)

    /** The user's tablet, about 1680 x 1050dp: the grid and pane keep their widths and center. */
    @Config(qualifiers = GOLDEN_TABLET_WIDE)
    @Test fun `entry-points hub tablet-wide light`() = capture("entry-points__hub", "tablet-wide", dark = false, state = hub)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `entry-points hub longest phone light`() = capture("entry-points__hub-longest", "phone", dark = false, state = longest)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `entry-points hub longest phone dark`() = capture("entry-points__hub-longest", "phone", dark = true, state = longest)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `entry-points hub longest tablet light`() = capture("entry-points__hub-longest", "tablet", dark = false, state = longest)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `entry-points hub longest tablet dark`() = capture("entry-points__hub-longest", "tablet", dark = true, state = longest)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `entry-points hub empty phone light`() = capture("entry-points__hub-empty", "phone", dark = false, state = InsightsHubUiState.Empty)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `entry-points hub empty phone dark`() = capture("entry-points__hub-empty", "phone", dark = true, state = InsightsHubUiState.Empty)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `entry-points hub empty tablet light`() = capture("entry-points__hub-empty", "tablet", dark = false, state = InsightsHubUiState.Empty)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `entry-points hub empty tablet dark`() = capture("entry-points__hub-empty", "tablet", dark = true, state = InsightsHubUiState.Empty)

    private val brands: (GameId) -> GameBrand = {
        GameBrand(if (it == alpha) AccentSlot.Blue else AccentSlot.Orange, GameBrand.Neutral.glyph)
    }

    private fun capture(name: String, device: String, dark: Boolean, state: InsightsHubUiState) {
        rule.showGolden(dark = dark) {
            CompositionLocalProvider(LocalGameBrands provides brands) { InsightsHubScreen(state, onOpen = {}, onBack = {}, onOpenStats = {}) }
        }
        // The entry sheen starts at 1.5s and runs about 0.7s; settle past it so no golden catches it mid-crossing.
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(SHEEN_CLEAR_MS)
        rule.settleAndCapture("hub", name, device, dark)
    }
}

private const val SHEEN_CLEAR_MS = 3_000L

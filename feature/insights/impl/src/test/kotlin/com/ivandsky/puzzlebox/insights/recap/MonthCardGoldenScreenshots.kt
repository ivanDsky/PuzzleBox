package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET_WIDE
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Month card renders on phone, tablet and a wide tablet, light and dark. Goldens in `snapshots/recap/month*`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MonthCardGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `month phone light`() = capture("month", "phone", dark = false, RecapFixtures.march)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `month phone dark`() = capture("month", "phone", dark = true, RecapFixtures.march)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `month tablet light`() = capture("month", "tablet", dark = false, RecapFixtures.march, RecapFixtures.april)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `month default phone light`() = capture("month-default", "phone", dark = false, MonthFixtures.january)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `month default phone dark`() = capture("month-default", "phone", dark = true, MonthFixtures.january)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `month empty phone light`() = capture("month-empty", "phone", dark = false, MonthFixtures.quiet)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `month longest phone light`() = capture("month-longest", "phone", dark = false, MonthFixtures.longest)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `month default tablet dark`() = capture("month-default", "tablet", dark = true, MonthFixtures.january, RecapFixtures.april)

    @Config(qualifiers = GOLDEN_TABLET_WIDE)
    @Test fun `month default tablet-wide light`() = capture("month-default", "tablet-wide", dark = false, MonthFixtures.january, RecapFixtures.april)

    @Config(qualifiers = GOLDEN_TABLET_WIDE)
    @Test fun `month longest tablet-wide light`() = capture("month-longest", "tablet-wide", dark = false, MonthFixtures.longest, RecapFixtures.april)

    /** The year deck opened on its first month, after the totals card. */
    private fun capture(name: String, device: String, dark: Boolean, vararg months: RecapCard.Month) {
        val state = readyYear("2026 so far", RecapFixtures.solved, *months).copy(initialPage = 1)
        rule.showGolden(dark = dark) {
            RecapScreen(state, "November 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {})
        }
        rule.settleAndCapture("recap", name, device, dark)
    }
}

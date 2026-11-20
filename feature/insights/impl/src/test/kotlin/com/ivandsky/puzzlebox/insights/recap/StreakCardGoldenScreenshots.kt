package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.recap.StreakFixtures.default
import com.ivandsky.puzzlebox.insights.recap.StreakFixtures.longest
import com.ivandsky.puzzlebox.insights.recap.StreakFixtures.noFreezes
import com.ivandsky.puzzlebox.insights.recap.StreakFixtures.year
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Streak card renders on phone and tablet, light and dark, in its four states. Goldens in `snapshots/recap/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StreakCardGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `streak default phone light`() = capture("streak-default", "phone", dark = false, month(default))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `streak default phone dark`() = capture("streak-default", "phone", dark = true, month(default))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `streak default tablet light`() = capture("streak-default", "tablet", dark = false, month(default))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `streak default tablet dark`() = capture("streak-default", "tablet", dark = true, month(default))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `streak no freezes phone light`() = capture("streak-no-freezes", "phone", dark = false, month(noFreezes))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `streak no freezes phone dark`() = capture("streak-no-freezes", "phone", dark = true, month(noFreezes))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `streak no freezes tablet light`() = capture("streak-no-freezes", "tablet", dark = false, month(noFreezes))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `streak no freezes tablet dark`() = capture("streak-no-freezes", "tablet", dark = true, month(noFreezes))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `streak longest phone light`() = capture("streak-longest", "phone", dark = false, month(longest))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `streak longest phone dark`() = capture("streak-longest", "phone", dark = true, month(longest))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `streak longest tablet light`() = capture("streak-longest", "tablet", dark = false, month(longest))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `streak longest tablet dark`() = capture("streak-longest", "tablet", dark = true, month(longest))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `streak year phone light`() = capture("streak-year", "phone", dark = false, year())

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `streak year phone dark`() = capture("streak-year", "phone", dark = true, year())

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `streak year tablet light`() = capture("streak-year", "tablet", dark = false, year())

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `streak year tablet dark`() = capture("streak-year", "tablet", dark = true, year())

    private fun capture(name: String, device: String, dark: Boolean, state: RecapUiState.Ready) {
        rule.showGolden(dark = dark) {
            RecapScreen(state, state.title, RecapFixtures.gameName, onPage = {}, onWatchReplay = {}, onClose = {})
        }
        rule.settleAndCapture("recap", name, device, dark)
    }

    private fun month(streak: RecapCard.Streak) = RecapUiState.Ready(
        period = RecapPeriod.Month(YearMonth.of(2026, 10)),
        title = "October 2026",
        cards = deck(streak, RecapFixtures.totalTime),
        initialPage = 0,
    ).onCard(2)

    private fun year() = RecapUiState.Ready(
        period = RecapPeriod.Year(2026),
        title = "2026 so far",
        cards = deck(year, RecapFixtures.totalTime),
        initialPage = 0,
    ).onCard(2)

    // The streak sits third, so the card opens on it whatever the deck starts with.
    private fun deck(streak: RecapCard.Streak, totalTime: RecapCard) =
        listOf(RecapFixtures.solved, RecapFixtures.fastest, streak, RecapFixtures.moments, RecapFixtures.friendsLoaded, totalTime)
}

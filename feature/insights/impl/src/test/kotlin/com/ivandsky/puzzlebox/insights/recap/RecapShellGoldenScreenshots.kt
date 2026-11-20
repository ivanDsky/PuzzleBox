package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.fastest
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.friendsLoaded
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.moments
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.solved
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.streak
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.totalTime
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import com.ivandsky.puzzlebox.insights.showGolden
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The story shell (strip, ↻ and close, the card deck) on the Streak card of a seven-page October story, at rest and held
 * to peek at Moments, on its Share page (last), loading and unable to open (error), on phone and tablet, light and dark. Goldens in `snapshots/story-shell/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RecapShellGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `mid phone light`() = capture("mid", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `mid phone dark`() = capture("mid", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `mid tablet light`() = capture("mid", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `mid tablet dark`() = capture("mid", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `hold phone light`() = capture("hold", "phone", dark = false, held = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `hold phone dark`() = capture("hold", "phone", dark = true, held = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `hold tablet light`() = capture("hold", "tablet", dark = false, held = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `hold tablet dark`() = capture("hold", "tablet", dark = true, held = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `last phone light`() = capture("last", "phone", dark = false, state = October.copy(initialPage = 6))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `last phone dark`() = capture("last", "phone", dark = true, state = October.copy(initialPage = 6))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `last tablet light`() = capture("last", "tablet", dark = false, state = October.copy(initialPage = 6))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `last tablet dark`() = capture("last", "tablet", dark = true, state = October.copy(initialPage = 6))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `loading phone light`() = capture("loading", "phone", dark = false, state = RecapUiState.Loading)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `loading phone dark`() = capture("loading", "phone", dark = true, state = RecapUiState.Loading)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `loading tablet light`() = capture("loading", "tablet", dark = false, state = RecapUiState.Loading)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `loading tablet dark`() = capture("loading", "tablet", dark = true, state = RecapUiState.Loading)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `error phone light`() = capture("error", "phone", dark = false, state = RecapUiState.Error)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `error phone dark`() = capture("error", "phone", dark = true, state = RecapUiState.Error)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `error tablet light`() = capture("error", "tablet", dark = false, state = RecapUiState.Error)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `error tablet dark`() = capture("error", "tablet", dark = true, state = RecapUiState.Error)

    private fun capture(name: String, device: String, dark: Boolean, held: Boolean = false, state: RecapUiState = October) {
        rule.showGolden(dark = dark) {
            RecapScreen(state, "October 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {}, share = Share)
        }
        if (held) rule.onNodeWithTag("recap.pager").performTouchInput { down(center) }
        rule.settleAndCapture("story-shell", name, device, dark)
    }
}

private val October = RecapUiState.Ready(
    period = RecapPeriod.Month(YearMonth.of(2026, 10)),
    title = "October 2026",
    cards = listOf(solved, fastest, streak, moments, friendsLoaded, totalTime),
    initialPage = 2,
)

private val Share = RecapShareState("October 2026", "Ivan", restoring = false, sharing = false, failed = false)

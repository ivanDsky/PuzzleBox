package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
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

/** The recap story on a phone and on the landscape tablet, light and dark, plus its waiting state. Goldens in `snapshots/recap/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RecapGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `story phone light`() = capture("story", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `story phone dark`() = capture("story", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `story tablet light`() = capture("story", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `story tablet dark`() = capture("story", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `story second card phone light`() = capture("story-second-card", "phone", dark = false, initialPage = 1)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `fastest phone light`() = capture("fastest", "phone", dark = false, state = readyOf(RecapFixtures.fastest))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `fastest phone dark`() = capture("fastest", "phone", dark = true, state = readyOf(RecapFixtures.fastest))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `streak phone light`() = capture("streak", "phone", dark = false, state = readyOf(RecapFixtures.streak))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `streak phone dark`() = capture("streak", "phone", dark = true, state = readyOf(RecapFixtures.streak))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `fastest tablet light`() = capture("fastest", "tablet", dark = false, state = readyOf(RecapFixtures.fastest, RecapFixtures.streak))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `streak tablet dark`() = capture("streak", "tablet", dark = true, state = readyOf(RecapFixtures.fastest, RecapFixtures.streak).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `hint-free phone light`() = capture("hint-free", "phone", dark = false, state = readyOf(RecapFixtures.hintFree))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `hint-free phone dark`() = capture("hint-free", "phone", dark = true, state = readyOf(RecapFixtures.hintFree))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `hint-free tablet light`() = capture("hint-free", "tablet", dark = false, state = readyOf(RecapFixtures.solved, RecapFixtures.hintFree).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `hint-free tablet dark`() = capture("hint-free", "tablet", dark = true, state = readyOf(RecapFixtures.solved, RecapFixtures.hintFree).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `moments phone light`() = capture("moments", "phone", dark = false, state = readyOf(RecapFixtures.moments))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `moments phone dark`() = capture("moments", "phone", dark = true, state = readyOf(RecapFixtures.moments))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `moments tablet light`() = capture("moments", "tablet", dark = false, state = readyOf(RecapFixtures.solved, RecapFixtures.moments).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `moments tablet dark`() = capture("moments", "tablet", dark = true, state = readyOf(RecapFixtures.solved, RecapFixtures.moments).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time phone light`() = capture("total-time", "phone", dark = false, state = readyOf(RecapFixtures.totalTime))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time phone dark`() = capture("total-time", "phone", dark = true, state = readyOf(RecapFixtures.totalTime))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time tablet light`() = capture("total-time", "tablet", dark = false, state = readyOf(RecapFixtures.solved, RecapFixtures.totalTime).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time tablet dark`() = capture("total-time", "tablet", dark = true, state = readyOf(RecapFixtures.solved, RecapFixtures.totalTime).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time-year phone light`() = capture("total-time-year", "phone", dark = false, state = readyOf(RecapFixtures.totalTimeYear))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends reading phone light`() = capture("friends-reading", "phone", dark = false, state = readyOf(RecapFixtures.friendsReading))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends reading phone dark`() = capture("friends-reading", "phone", dark = true, state = readyOf(RecapFixtures.friendsReading))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends loaded phone light`() = capture("friends-loaded", "phone", dark = false, state = readyOf(RecapFixtures.friendsLoaded))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends loaded phone dark`() = capture("friends-loaded", "phone", dark = true, state = readyOf(RecapFixtures.friendsLoaded))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends settling phone light`() = capture("friends-settling", "phone", dark = false, state = readyOf(RecapFixtures.friendsSettling))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends settling phone dark`() = capture("friends-settling", "phone", dark = true, state = readyOf(RecapFixtures.friendsSettling))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends unavailable phone light`() = capture("friends-unavailable", "phone", dark = false, state = readyOf(RecapFixtures.friendsUnavailable))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends unavailable phone dark`() = capture("friends-unavailable", "phone", dark = true, state = readyOf(RecapFixtures.friendsUnavailable))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends tablet light`() = capture("friends", "tablet", dark = false, state = readyOf(RecapFixtures.solved, RecapFixtures.friendsLoaded).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends settling tablet dark`() = capture("friends-settling", "tablet", dark = true, state = readyOf(RecapFixtures.solved, RecapFixtures.friendsSettling).copy(initialPage = 1))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `restoring phone light`() = capture("restoring", "phone", dark = false, state = RecapUiState.Restoring)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `empty phone light`() = capture("empty", "phone", dark = false, state = RecapUiState.Empty)

    private fun capture(
        name: String,
        device: String,
        dark: Boolean,
        initialPage: Int = 0,
        state: RecapUiState = ready.copy(initialPage = initialPage),
    ) {
        rule.showGolden(dark = dark) {
            RecapScreen(state, "November 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {})
        }
        rule.settleAndCapture("recap", name, device, dark)
    }
}

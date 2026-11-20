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

/** Friends card renders on phone and tablet, light and dark. Goldens in `snapshots/recap/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FriendsCardGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

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
    @Test fun `year friends phone light`() = capture("year-friends", "phone", dark = false, state = readyYear("2026 so far", RecapFixtures.friendsLoadedYear))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `year friends phone dark`() = capture("year-friends", "phone", dark = true, state = readyYear("2026 so far", RecapFixtures.friendsLoadedYear))

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

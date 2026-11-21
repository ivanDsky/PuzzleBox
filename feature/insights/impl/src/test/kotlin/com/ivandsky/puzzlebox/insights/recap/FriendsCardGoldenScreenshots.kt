package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
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
    @Test fun `friends loaded phone light`() = capture("friends-loaded", "phone", dark = false, state = readyOf(FriendsFixtures.loaded))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends loaded phone dark`() = capture("friends-loaded", "phone", dark = true, state = readyOf(FriendsFixtures.loaded))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends loaded tablet light`() = capture("friends-loaded", "tablet", dark = false, state = FriendsFixtures.story(FriendsFixtures.loaded))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends loaded tablet dark`() = capture("friends-loaded", "tablet", dark = true, state = FriendsFixtures.story(FriendsFixtures.loaded))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends reading phone light`() = capture("friends-reading", "phone", dark = false, state = readyOf(FriendsFixtures.reading))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends reading phone dark`() = capture("friends-reading", "phone", dark = true, state = readyOf(FriendsFixtures.reading))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends reading tablet light`() = capture("friends-reading", "tablet", dark = false, state = FriendsFixtures.story(FriendsFixtures.reading))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends reading tablet dark`() = capture("friends-reading", "tablet", dark = true, state = FriendsFixtures.story(FriendsFixtures.reading))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends settling phone light`() = capture("friends-settling", "phone", dark = false, state = readyOf(FriendsFixtures.settling))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends settling phone dark`() = capture("friends-settling", "phone", dark = true, state = readyOf(FriendsFixtures.settling))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends settling tablet light`() = capture("friends-settling", "tablet", dark = false, state = FriendsFixtures.story(FriendsFixtures.settling))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends settling tablet dark`() = capture("friends-settling", "tablet", dark = true, state = FriendsFixtures.story(FriendsFixtures.settling))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends longest phone light`() = capture("friends-longest", "phone", dark = false, state = readyOf(FriendsFixtures.longest))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends longest phone dark`() = capture("friends-longest", "phone", dark = true, state = readyOf(FriendsFixtures.longest))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends longest tablet light`() = capture("friends-longest", "tablet", dark = false, state = FriendsFixtures.story(FriendsFixtures.longest))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends longest tablet dark`() = capture("friends-longest", "tablet", dark = true, state = FriendsFixtures.story(FriendsFixtures.longest))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends year phone light`() = capture("friends-year", "phone", dark = false, state = readyYear("2026 so far", FriendsFixtures.year))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends year phone dark`() = capture("friends-year", "phone", dark = true, state = readyYear("2026 so far", FriendsFixtures.year))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends year tablet light`() = capture("friends-year", "tablet", dark = false, state = FriendsFixtures.story(FriendsFixtures.year))

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friends year tablet dark`() = capture("friends-year", "tablet", dark = true, state = FriendsFixtures.story(FriendsFixtures.year))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends unavailable phone light`() = capture("friends-unavailable", "phone", dark = false, state = readyOf(FriendsFixtures.unavailable))

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `friends unavailable phone dark`() = capture("friends-unavailable", "phone", dark = true, state = readyOf(FriendsFixtures.unavailable))

    private fun capture(
        name: String,
        device: String,
        dark: Boolean,
        state: RecapUiState,
    ) {
        rule.showGolden(dark = dark) {
            CompositionLocalProvider(LocalGameBrands provides FriendsFixtures.brands) {
                RecapScreen(state, if (name == "friends-year") "2026 so far" else "October 2026", FriendsFixtures.gameName, onPage = {}, onWatchReplay = {}, onClose = {})
            }
        }
        rule.settleAndCapture("recap", name, device, dark)
    }
}

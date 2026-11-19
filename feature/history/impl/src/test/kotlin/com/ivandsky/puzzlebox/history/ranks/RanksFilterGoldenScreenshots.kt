package com.ivandsky.puzzlebox.history.ranks

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.GOLDEN_PHONE
import com.ivandsky.puzzlebox.history.GOLDEN_TABLET
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import com.ivandsky.puzzlebox.history.settleAndCapture
import com.ivandsky.puzzlebox.history.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ranks with the Who and When chips on, light and dark: at the defaults, tuned to Friends and Week, and (phone) with the Who sheet open.
 * Record-only review captures in `snapshots/ranks-filters/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class RanksFilterPhoneGoldenScreenshots : RanksFilterGoldenScreenshots("phone", HistoryLayout.SinglePane) {
    @Test fun `default light`() = capture("default", Default, dark = false)
    @Test fun `default dark`() = capture("default", Default, dark = true)

    @Test fun `tuned light`() = capture("tuned", Tuned, dark = false)
    @Test fun `tuned dark`() = capture("tuned", Tuned, dark = true)

    @Test fun `who sheet light`() = capture("who-sheet", Default, dark = false, open = "ranks.filter.who")
    @Test fun `who sheet dark`() = capture("who-sheet", Default, dark = true, open = "ranks.filter.who")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class RanksFilterTabletGoldenScreenshots : RanksFilterGoldenScreenshots("tablet", HistoryLayout.TwoPane) {
    @Test fun `default light`() = capture("default", Default, dark = false)
    @Test fun `default dark`() = capture("default", Default, dark = true)

    @Test fun `tuned light`() = capture("tuned", Tuned, dark = false)
    @Test fun `tuned dark`() = capture("tuned", Tuned, dark = true)
}

abstract class RanksFilterGoldenScreenshots(private val device: String, private val layout: HistoryLayout) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    internal fun capture(name: String, state: RanksUiState, dark: Boolean, open: String? = null) {
        rule.showGolden(dark) {
            RanksScreen(state, RanksActions(onSignIn = {}, onSwipe = {}), layout = layout, tuneDefaults = previewTuneDefaults())
        }
        open?.let { rule.onNodeWithTag(it).performClick() }
        rule.settleAndCapture("ranks-filters", name, device, dark)
    }

    internal companion object {
        val Default = RanksGoldenScreenshots.FriendsDay.copy(scope = RankScope.Global, window = Window.Day, filterSheets = true)
        val Tuned = Default.copy(scope = RankScope.Friends, window = Window.Week)
    }
}

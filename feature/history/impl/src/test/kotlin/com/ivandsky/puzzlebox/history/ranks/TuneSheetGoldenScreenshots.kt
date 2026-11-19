package com.ivandsky.puzzlebox.history.ranks

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.GOLDEN_PHONE
import com.ivandsky.puzzlebox.history.GOLDEN_TABLET
import com.ivandsky.puzzlebox.history.RanksTuneDefaults
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import com.ivandsky.puzzlebox.history.settleAndCapture
import com.ivandsky.puzzlebox.history.showGolden
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Tune sheet with its Defaults block, and without it (RanksTuneBoardOnly on), on a phone, light and dark. Goldens in `snapshots/ranks/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TuneSheetPhoneGoldenScreenshots : TuneSheetGoldenScreenshots("phone") {
    @Test fun `tune defaults light`() = capture("tune-defaults", withDefaults = true, dark = false)
    @Test fun `tune defaults dark`() = capture("tune-defaults", withDefaults = true, dark = true)
    @Test fun `tune board only light`() = capture("tune-board-only", withDefaults = false, dark = false)
    @Test fun `tune board only dark`() = capture("tune-board-only", withDefaults = false, dark = true)
}

/** The same sheet on the landscape tablet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class TuneSheetTabletGoldenScreenshots : TuneSheetGoldenScreenshots("tablet") {
    @Test fun `tune defaults light`() = capture("tune-defaults", withDefaults = true, dark = false)
    @Test fun `tune board only light`() = capture("tune-board-only", withDefaults = false, dark = false)
    @Test fun `tune board only dark`() = capture("tune-board-only", withDefaults = false, dark = true)
}

abstract class TuneSheetGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val defaults = RanksTuneDefaults(
        ownTier = MutableStateFlow(OwnTier.Chosen(VisibilityTier.Duel)),
        onSelectTier = {},
        friendsDefault = MutableStateFlow(true),
        onSelectFriendsDefault = {},
    )

    internal fun capture(name: String, withDefaults: Boolean, dark: Boolean) {
        rule.showGolden(dark) {
            TuneSheet(RankScope.Global, Window.Day, onScope = {}, onWindow = {}, onDismiss = {}, defaults = defaults.takeIf { withDefaults })
        }
        rule.settleAndCapture("ranks", name, device, dark)
    }
}

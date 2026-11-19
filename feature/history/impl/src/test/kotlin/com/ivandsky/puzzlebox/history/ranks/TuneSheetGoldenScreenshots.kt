package com.ivandsky.puzzlebox.history.ranks

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.history.GOLDEN_PHONE
import com.ivandsky.puzzlebox.history.GOLDEN_TABLET
import com.ivandsky.puzzlebox.history.RanksTuneDefaults
import com.ivandsky.puzzlebox.history.settleAndCapture
import com.ivandsky.puzzlebox.history.showGolden
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Tune sheet with its Defaults block on a phone, light and dark. Goldens in `snapshots/ranks/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TuneSheetPhoneGoldenScreenshots : TuneSheetGoldenScreenshots("phone") {
    @Test fun `tune defaults light`() = capture("tune-defaults", dark = false)
    @Test fun `tune defaults dark`() = capture("tune-defaults", dark = true)
}

/** The same sheet on the landscape tablet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class TuneSheetTabletGoldenScreenshots : TuneSheetGoldenScreenshots("tablet") {
    @Test fun `tune defaults light`() = capture("tune-defaults", dark = false)
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

    internal fun capture(name: String, dark: Boolean) {
        rule.showGolden(dark) {
            TuneSheet(defaults, onDismiss = {})
        }
        rule.settleAndCapture("ranks", name, device, dark)
    }
}

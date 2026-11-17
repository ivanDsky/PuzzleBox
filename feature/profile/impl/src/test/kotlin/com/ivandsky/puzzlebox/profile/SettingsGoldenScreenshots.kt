package com.ivandsky.puzzlebox.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.profile.settings.SettingsScreen
import com.ivandsky.puzzlebox.profile.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings with its Haptics (Vibration) section, the screen a light-theme signed-in user sees on a 360dp phone and the landscape tablet, light. Goldens in `snapshots/settings/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = "w360dp-h800dp-420dpi")
    @Test fun `vibration phone light`() = capture("phone")

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `vibration tablet light`() = capture("tablet")

    private fun capture(device: String) {
        rule.showGolden(dark = false) {
            SettingsScreen(
                state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", visibility = OwnTier.Unset),
                onSetThemeMode = {},
                onSetQueensAutoCross = {},
                onSetSudokuPeerHighlight = {},
                onSetSudokuSameDigitHighlight = {},
                onBack = {},
                onOpenCatalog = null,
            )
        }
        rule.settleAndCapture("settings", "vibration", device, dark = false)
    }
}

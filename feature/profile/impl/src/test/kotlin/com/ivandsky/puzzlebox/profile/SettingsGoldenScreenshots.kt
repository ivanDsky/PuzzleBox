package com.ivandsky.puzzlebox.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.profile.settings.SettingsScreen
import com.ivandsky.puzzlebox.profile.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings with its Haptics (Vibration) section and its Play section: the screen a light-theme signed-in user sees on a 360dp phone and the landscape tablet, light. Goldens in `snapshots/settings/`. */
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

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `play on phone light`() = capturePlay("phone", timeLeft = true, dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `play on phone dark`() = capturePlay("phone", timeLeft = true, dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `play on tablet light`() = capturePlay("tablet", timeLeft = true, dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `play on tablet dark`() = capturePlay("tablet", timeLeft = true, dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `play off phone light`() = capturePlay("phone", timeLeft = false, dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `play off phone dark`() = capturePlay("phone", timeLeft = false, dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `play off tablet light`() = capturePlay("tablet", timeLeft = false, dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `play off tablet dark`() = capturePlay("tablet", timeLeft = false, dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `signed out phone light`() = captureSignedOut("phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `signed out phone dark`() = captureSignedOut("phone", dark = true)

    private fun captureSignedOut(device: String, dark: Boolean) {
        rule.showGolden(dark = dark) {
            SettingsScreen(
                state = SettingsUiState(
                    themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                    versionName = "1.4.0",
                    visibility = OwnTier.Unknown,
                ),
                onSetThemeMode = {},
                onSetQueensAutoCross = {},
                onSetSudokuPeerHighlight = {},
                onSetSudokuSameDigitHighlight = {},
                onBack = {},
                onOpenCatalog = null,
            )
        }
        rule.settleAndCapture("settings", "signed-out", device, dark = dark)
    }

    private fun capturePlay(device: String, timeLeft: Boolean, dark: Boolean) {
        rule.showGolden(dark = dark) {
            SettingsScreen(
                state = SettingsUiState(
                    themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                    versionName = "1.4.0",
                    visibility = OwnTier.Chosen(VisibilityTier.Board),
                    showSelfDuelTimeLeft = timeLeft,
                ),
                onSetThemeMode = {},
                onSetQueensAutoCross = {},
                onSetSudokuPeerHighlight = {},
                onSetSudokuSameDigitHighlight = {},
                onBack = {},
                onOpenCatalog = null,
            )
        }
        rule.settleAndCapture("settings", "play-${if (timeLeft) "on" else "off"}", device, dark = dark)
    }

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

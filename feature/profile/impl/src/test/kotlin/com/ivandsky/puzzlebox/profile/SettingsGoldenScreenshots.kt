package com.ivandsky.puzzlebox.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.profile.settings.SettingsScreenPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings with its Feedback (Vibration) section on a 360dp phone and the landscape tablet, light. Goldens in `snapshots/settings/`. */
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
        rule.showGolden(dark = false) { SettingsScreenPreview() }
        rule.settleAndCapture("settings", "vibration", device, dark = false)
    }
}

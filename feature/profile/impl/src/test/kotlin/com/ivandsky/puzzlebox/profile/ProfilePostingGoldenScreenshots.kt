package com.ivandsky.puzzlebox.profile

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.profile.linkedin.LinkedInPostingWarningPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The posting switch on Profile's LinkedIn row (off, on, paused) and its warning, on the golden phone, light and dark.
 * The screens are the slim previews. Goldens in `snapshots/posting/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ProfilePostingGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `switch off light`() = capture("switch-off", dark = false) { ProfileLinkedInPostingOffPreview() }
    @Test fun `switch off dark`() = capture("switch-off", dark = true) { ProfileLinkedInPostingOffPreview() }

    @Test fun `switch on light`() = capture("switch-on", dark = false) { ProfileLinkedInPostingOnPreview() }
    @Test fun `switch on dark`() = capture("switch-on", dark = true) { ProfileLinkedInPostingOnPreview() }

    @Test fun `switch paused light`() = capture("switch-paused", dark = false) { ProfileLinkedInPostingPausedPreview() }
    @Test fun `switch paused dark`() = capture("switch-paused", dark = true) { ProfileLinkedInPostingPausedPreview() }

    @Test fun `warning light`() = capture("warning", dark = false) { LinkedInPostingWarningPreview() }
    @Test fun `warning dark`() = capture("warning", dark = true) { LinkedInPostingWarningPreview() }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.showGolden(dark, content = content)
        rule.settleAndCapture("posting", name, "phone", dark)
    }
}

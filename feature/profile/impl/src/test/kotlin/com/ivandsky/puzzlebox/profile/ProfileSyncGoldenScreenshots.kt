package com.ivandsky.puzzlebox.profile

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Profile with cloud sync on, on the golden phone, light and dark: signed in and Synced (with the LinkedIn row and its
 * board line), the worst case (Offline, three oversized drafts, an erase message and a delete-boards message), and signed out
 * with sync available (the sign-in copy and the LinkedIn sign-in hint). The screens are the slim previews.
 * Goldens in `snapshots/sync/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ProfileSyncGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `synced light`() = capture("synced", dark = false) { ProfileSlimSyncedPreview() }
    @Test fun `synced dark`() = capture("synced", dark = true) { ProfileSlimSyncedPreview() }

    @Test fun `worst case light`() = capture("worst-case", dark = false) { ProfileSlimWorstCasePreview() }
    @Test fun `worst case dark`() = capture("worst-case", dark = true) { ProfileSlimWorstCasePreview() }

    @Test fun `signed out with sync light`() = capture("signed-out", dark = false) { ProfileLinkedInSignInHintPreview() }
    @Test fun `signed out with sync dark`() = capture("signed-out", dark = true) { ProfileLinkedInSignInHintPreview() }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.showGolden(dark, content = content)
        rule.settleAndCapture("sync", name, "phone", dark)
    }
}

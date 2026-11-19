package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import com.ivandsky.puzzlebox.insights.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The story's Share page on a phone, light and dark, ready and while the history restores. Goldens in `snapshots/recap/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ShareCardGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ready = RecapShareState("November 2026", "Ada", restoring = false, sharing = false, failed = false)

    @Test fun `share phone light`() = capture("share", ready, dark = false)

    @Test fun `share phone dark`() = capture("share", ready, dark = true)

    @Test fun `share restoring phone light`() = capture("share-restoring", ready.copy(restoring = true), dark = false)

    private fun capture(name: String, share: RecapShareState, dark: Boolean) {
        rule.showGolden(dark = dark) {
            RecapScreen(
                RecapFixtures.ready.copy(initialPage = 2), "November 2026", gameName,
                onPage = {}, onWatchReplay = {}, onClose = {}, share = share,
            )
        }
        rule.settleAndCapture("recap", name, "phone", dark)
    }
}

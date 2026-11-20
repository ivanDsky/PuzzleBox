package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import com.ivandsky.puzzlebox.insights.share.ShareContract
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

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share longest phone light`() = contract("longest", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share longest phone dark`() = contract("longest", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share longest tablet light`() = contract("longest", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share longest tablet dark`() = contract("longest", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share empty phone light`() = contract("empty", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share empty phone dark`() = contract("empty", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share empty tablet light`() = contract("empty", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share empty tablet dark`() = contract("empty", "tablet", dark = true)

    private fun capture(name: String, share: RecapShareState, dark: Boolean) {
        rule.showGolden(dark = dark) {
            RecapScreen(
                RecapFixtures.ready.copy(initialPage = 2), "November 2026", gameName,
                onPage = {}, onWatchReplay = {}, onClose = {}, share = share,
            )
        }
        rule.settleAndCapture("recap", name, "phone", dark)
    }

    /** The contract's [state] ("longest" or "empty") on the Share page, built inline from the contract dataset. */
    private fun contract(state: String, device: String, dark: Boolean) {
        val longest = state == "longest"
        val cards = if (longest) ShareContract.longest else ShareContract.empty
        val shell = if (longest) listOf(RecapFixtures.fastest, RecapFixtures.friendsLoaded) else listOf(RecapFixtures.fastest)
        val all = listOf(cards[0]) + shell + cards.drop(1)
        val name = if (longest) ShareContract.LONG_NAME else "Ivan"
        rule.showGolden(dark = dark) {
            CompositionLocalProvider(LocalGameAccent provides SolvedFixtures.accentOf) {
                RecapScreen(
                    readyOf(*all.toTypedArray()).copy(title = "October 2026", initialPage = all.size),
                    "October 2026", SolvedFixtures.gameName,
                    onPage = {}, onWatchReplay = {}, onClose = {},
                    share = RecapShareState("October 2026", name, restoring = false, sharing = false, failed = false),
                )
            }
        }
        rule.settleAndCapture("recap", "share-$state", device, dark)
    }
}

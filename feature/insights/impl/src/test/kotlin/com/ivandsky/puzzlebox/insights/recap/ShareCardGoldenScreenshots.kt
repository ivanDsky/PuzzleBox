package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share default phone light`() = pick("default", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share default phone dark`() = pick("default", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share default tablet light`() = pick("default", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share default tablet dark`() = pick("default", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share color phone light`() = pick("color", "phone", dark = false, style = "Color")

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share color phone dark`() = pick("color", "phone", dark = true, style = "Color")

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share color tablet light`() = pick("color", "tablet", dark = false, style = "Color")

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share color tablet dark`() = pick("color", "tablet", dark = true, style = "Color")

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share night phone light`() = pick("night", "phone", dark = false, style = "Night")

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share night phone dark`() = pick("night", "phone", dark = true, style = "Night")

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share night tablet light`() = pick("night", "tablet", dark = false, style = "Night")

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share night tablet dark`() = pick("night", "tablet", dark = true, style = "Night")

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share name hidden phone light`() = pick("name-hidden", "phone", dark = false, hideName = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share name hidden phone dark`() = pick("name-hidden", "phone", dark = true, hideName = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share name hidden tablet light`() = pick("name-hidden", "tablet", dark = false, hideName = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share name hidden tablet dark`() = pick("name-hidden", "tablet", dark = true, hideName = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share error phone light`() = pick("error", "phone", dark = false, failed = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `share error phone dark`() = pick("error", "phone", dark = true, failed = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share error tablet light`() = pick("error", "tablet", dark = false, failed = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `share error tablet dark`() = pick("error", "tablet", dark = true, failed = true)

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

    /**
     * The contract's [state] frame: the October dataset on the Share page, after the deck has arrived, with the [style]
     * chip tapped (Classic when null), the Name chip turned off when [hideName], and the last share [failed].
     */
    private fun pick(state: String, device: String, dark: Boolean, style: String? = null, hideName: Boolean = false, failed: Boolean = false) {
        val shell = listOf(RecapFixtures.fastest, RecapFixtures.friendsLoaded)
        val all = listOf(ShareContract.default[0]) + shell + ShareContract.default.drop(1)
        rule.showGolden(dark = dark) {
            CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) {
                RecapScreen(
                    readyOf(*all.toTypedArray()).copy(title = "October 2026", initialPage = all.size),
                    "October 2026", SolvedFixtures.gameName,
                    onPage = {}, onWatchReplay = {}, onClose = {},
                    share = RecapShareState("October 2026", "Ivan", restoring = false, sharing = false, failed = failed),
                )
            }
        }
        rule.mainClock.advanceTimeBy(ARRIVAL_MS)
        style?.let { rule.onNodeWithTag("recap.share.style.$it").performClick() }
        if (hideName) rule.onNodeWithTag("recap.share.name").performClick()
        rule.settleAndCapture("recap", "share-$state", device, dark)
    }

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
            CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) {
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

    private companion object {
        const val ARRIVAL_MS = 500L
    }
}

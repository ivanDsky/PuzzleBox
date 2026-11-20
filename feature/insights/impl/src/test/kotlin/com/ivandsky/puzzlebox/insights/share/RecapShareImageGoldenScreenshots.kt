package com.ivandsky.puzzlebox.insights.share

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The share image itself, 1080 by 1920 px at 1 px per dp: a month and a year still under way (A22), light and dark. Goldens in `snapshots/share/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = IMAGE_DEVICE)
class RecapShareImageGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val cards = listOf(RecapFixtures.solved, RecapFixtures.totalTime, RecapFixtures.streak, RecapFixtures.moments)

    @Test fun `month light`() = capture("month", "November 2026", "Ada", dark = false)

    @Test fun `month dark`() = capture("month", "November 2026", "Ada", dark = true)

    @Test fun `year so far light`() = capture("year-so-far", "2026 so far", "Ada", dark = false)

    @Test fun `year so far dark`() = capture("year-so-far", "2026 so far", "Ada", dark = true)

    @Test fun `signed out light`() = capture("signed-out", "November 2026", null, dark = false)

    @Test fun `few numbers dark`() = capture("few", "2026 so far", "A name that is much too long to fit on a single line of the image", dark = true, cards = listOf(RecapFixtures.solved, RecapFixtures.streak))

    private fun capture(name: String, title: String, displayName: String?, dark: Boolean, cards: List<RecapCard> = this.cards) {
        rule.showGolden(dark = dark) { RecapShareImage(RecapShareContent.of(title, displayName, cards)) }
        rule.settleAndCapture("share", name, "image", dark)
    }
}

private const val IMAGE_DEVICE = "w1080dp-h1920dp-mdpi"

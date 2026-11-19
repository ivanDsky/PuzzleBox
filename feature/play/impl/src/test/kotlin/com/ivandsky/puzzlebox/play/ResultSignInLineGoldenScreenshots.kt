package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.play.ResultHeroGoldenScreenshots.Companion.BRAND
import com.ivandsky.puzzlebox.play.ResultHeroGoldenScreenshots.Companion.facts
import com.ivandsky.puzzlebox.play.friend.UnderUpBar
import com.ivandsky.puzzlebox.resultui.ResultHeroActions
import com.ivandsky.puzzlebox.resultui.ResultHeroFacts
import com.ivandsky.puzzlebox.resultui.ResultHeroScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Today's Results hero with the sign-in line on (Result hero polish), signed out (the line under the time) and signed in
 * (the rank chip's page, no line). Phone and tablet, light and dark. Goldens in `snapshots/results-hero/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ResultSignInLineGoldenScreenshotsPhone : ResultSignInLineGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ResultSignInLineGoldenScreenshotsTablet : ResultSignInLineGoldenScreenshots("tablet")

abstract class ResultSignInLineGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `signed out light`() = capture("signin-line-signed-out", SIGNED_OUT, dark = false)
    @Test fun `signed out dark`() = capture("signin-line-signed-out", SIGNED_OUT, dark = true)

    @Test fun `signed in light`() = capture("signin-line-signed-in", SIGNED_IN, dark = false)
    @Test fun `signed in dark`() = capture("signin-line-signed-in", SIGNED_IN, dark = true)

    private fun capture(name: String, facts: ResultHeroFacts, dark: Boolean) {
        rule.showGolden(dark, animated = true) {
            UnderUpBar { Page(facts) }
        }
        rule.settleAndCapture("results-hero", name, device, dark)
    }

    @Composable
    private fun ColumnScope.Page(facts: ResultHeroFacts) {
        ResultHeroScreen(
            facts = facts,
            onSignIn = {},
            modifier = Modifier.weight(1f),
            brand = BRAND,
            actions = { ResultHeroActions(facts.primary, onPrimary = {}, onShare = {}, onPlayAgain = {}) },
        )
    }

    private companion object {
        val SIGNED_OUT = facts(rank = ResultRank.SignedOut).copy(signInLine = true)
        val SIGNED_IN = facts().copy(signInLine = true)
    }
}

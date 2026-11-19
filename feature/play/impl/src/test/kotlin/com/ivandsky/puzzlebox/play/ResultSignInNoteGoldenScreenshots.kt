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
 * The Results hero of a signed-out player whose Sign in did not finish: the rank row names the problem under its
 * prompt and keeps the button. Tablet only, light and dark: the phone page puts the rank row below the first screen. Goldens in `snapshots/results-hero/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ResultSignInNoteGoldenScreenshotsTablet : ResultSignInNoteGoldenScreenshots("tablet")

abstract class ResultSignInNoteGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `failed light`() = capture("signin-failed", "Couldn't sign in. Try again.", dark = false)
    @Test fun `failed dark`() = capture("signin-failed", "Couldn't sign in. Try again.", dark = true)

    @Test fun `offline light`() = capture("signin-offline", "You're offline. Sign in once you're back.", dark = false)
    @Test fun `offline dark`() = capture("signin-offline", "You're offline. Sign in once you're back.", dark = true)

    private fun capture(name: String, note: String, dark: Boolean) {
        val facts = facts(rank = ResultRank.SignedOut)
        rule.showGolden(dark, animated = true) {
            UnderUpBar { Page(facts, note) }
        }
        rule.settleAndCapture("results-hero", name, device, dark)
    }

    @Composable
    private fun ColumnScope.Page(facts: ResultHeroFacts, note: String) {
        ResultHeroScreen(
            facts = facts,
            onSignIn = {},
            signInNote = note,
            modifier = Modifier.weight(1f),
            brand = BRAND,
            actions = { ResultHeroActions(facts.primary, onPrimary = {}, onShare = {}, onPlayAgain = {}) },
        )
    }
}

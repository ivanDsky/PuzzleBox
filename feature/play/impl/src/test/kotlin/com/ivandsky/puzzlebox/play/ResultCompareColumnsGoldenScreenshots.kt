package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.SamePuzzleSolves
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
 * The Results hero with the compare columns on (Result hero polish): PREVIOUS and its gap tag, and BEST, for a faster solve,
 * a slower one, a LinkedIn previous solve and a best that is the previous solve (no BEST column). The values sit at half the
 * hero time's size. Phone, the phone's narrowest width (360 dp) and tablet, light and dark. Goldens in `snapshots/results-hero/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ResultCompareColumnsGoldenScreenshotsPhone : ResultCompareColumnsGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ResultCompareColumnsGoldenScreenshotsNarrow : ResultCompareColumnsGoldenScreenshots("narrow")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ResultCompareColumnsGoldenScreenshotsTablet : ResultCompareColumnsGoldenScreenshots("tablet")

abstract class ResultCompareColumnsGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `faster light`() = capture("compare-faster", FASTER, dark = false)
    @Test fun `faster dark`() = capture("compare-faster", FASTER, dark = true)

    @Test fun `slower light`() = capture("compare-slower", SLOWER, dark = false)
    @Test fun `slower dark`() = capture("compare-slower", SLOWER, dark = true)

    @Test fun `linkedin light`() = capture("compare-linkedin", LINKEDIN, dark = false)
    @Test fun `linkedin dark`() = capture("compare-linkedin", LINKEDIN, dark = true)

    @Test fun `no best light`() = capture("compare-no-best", NO_BEST, dark = false)
    @Test fun `no best dark`() = capture("compare-no-best", NO_BEST, dark = true)

    @Test fun `hints light`() = capture("compare-hints", HINTS, dark = false)
    @Test fun `hints dark`() = capture("compare-hints", HINTS, dark = true)

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
        /** 1:42 against last time's 1:54, with a 1:41 best of its own: "−12s" in the game's colour. */
        val FASTER = facts(timeMs = 102_000L).copy(
            samePuzzle = SamePuzzleSolves(BestScore(114_000L, 0), best = BestScore(101_000L, 0), columns = true),
        )

        /** 1:54 against last time's 1:42 and a 1:12 best: "+12s" on the neutral container. */
        val SLOWER = facts(timeMs = 114_000L).copy(
            samePuzzle = SamePuzzleSolves(BestScore(102_000L, 0), best = BestScore(72_000L, 0), columns = true),
        )

        val LINKEDIN = facts(timeMs = 102_000L).copy(
            samePuzzle = SamePuzzleSolves(BestScore(114_000L, 0), previousFromLinkedIn = true, best = BestScore(101_000L, 0), columns = true),
        )

        /** A hints-first game with fewer hints than last time: hint counts and "−1 hint". */
        val HINTS = facts(timeMs = 102_000L, hints = 1, order = RankOrder.HintsThenTime).copy(
            samePuzzle = SamePuzzleSolves(BestScore(114_000L, 2), best = BestScore(150_000L, 0), columns = true),
        )

        /** The previous solve is the best: PREVIOUS alone. */
        val NO_BEST = facts(timeMs = 114_000L).copy(
            samePuzzle = SamePuzzleSolves(BestScore(102_000L, 0), best = BestScore(102_000L, 0), columns = true),
        )
    }
}

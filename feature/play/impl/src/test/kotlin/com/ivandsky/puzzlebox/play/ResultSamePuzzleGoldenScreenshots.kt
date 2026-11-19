package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.play.ResultHeroGoldenScreenshots.Companion.BRAND
import com.ivandsky.puzzlebox.play.ResultHeroGoldenScreenshots.Companion.facts
import com.ivandsky.puzzlebox.play.friend.UnderUpBar
import com.ivandsky.puzzlebox.resultui.ResultHeroActions
import com.ivandsky.puzzlebox.resultui.ResultHeroFacts
import com.ivandsky.puzzlebox.resultui.ResultHeroScreen
import com.ivandsky.puzzlebox.resultui.SamePuzzleSolves
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Results hero with the same-puzzle comparison on (Result moments): faster than last time with a best of its own,
 * slower, against a LinkedIn solve, and a first solve of the puzzle that keeps the game-wide wording. The comparison
 * line is drawn at about half the time's size. Phone and tablet, light and dark. Goldens in `snapshots/results-hero/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ResultSamePuzzleGoldenScreenshotsPhone : ResultSamePuzzleGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ResultSamePuzzleGoldenScreenshotsTablet : ResultSamePuzzleGoldenScreenshots("tablet")

abstract class ResultSamePuzzleGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `faster light`() = capture("same-faster", FASTER, dark = false)
    @Test fun `faster dark`() = capture("same-faster", FASTER, dark = true)

    @Test fun `slower light`() = capture("same-slower", SLOWER, dark = false)
    @Test fun `slower dark`() = capture("same-slower", SLOWER, dark = true)

    @Test fun `linkedin light`() = capture("same-linkedin", LINKEDIN, dark = false)
    @Test fun `linkedin dark`() = capture("same-linkedin", LINKEDIN, dark = true)

    @Test fun `first solve light`() = capture("same-first-solve", FIRST_SOLVE, dark = false)
    @Test fun `first solve dark`() = capture("same-first-solve", FIRST_SOLVE, dark = true)

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
        /** 1:42 against last time's 1:54, with a 1:41 best of its own on this puzzle. */
        val FASTER = facts(timeMs = 102_000L).copy(
            samePuzzle = SamePuzzleSolves(BestScore(114_000L, 0), best = BestScore(101_000L, 0)),
        )

        /** 1:54 against last time's 1:42, which is also the best on this puzzle: no second line. */
        val SLOWER = facts(timeMs = 114_000L).copy(
            samePuzzle = SamePuzzleSolves(BestScore(102_000L, 0), best = BestScore(102_000L, 0)),
        )

        val LINKEDIN = facts(timeMs = 102_000L).copy(
            samePuzzle = SamePuzzleSolves(BestScore(114_000L, 0), previousFromLinkedIn = true, best = BestScore(114_000L, 0)),
        )

        /** No earlier solve of this puzzle: today's first-solve wording, at the larger size. */
        val FIRST_SOLVE = facts(
            verdict = RecordVerdict.FirstSolve, previous = null, earlier = emptyList(), streak = 1,
            rank = ResultRank.Hidden, doneToday = 1,
        ).copy(samePuzzle = SamePuzzleSolves.None)
    }
}

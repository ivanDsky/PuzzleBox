package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.SamePuzzleSolves
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.ResultHeroGoldenScreenshots.Companion.BRAND
import com.ivandsky.puzzlebox.play.ResultHeroGoldenScreenshots.Companion.facts
import com.ivandsky.puzzlebox.play.friend.UnderUpBar
import com.ivandsky.puzzlebox.resultui.ResultHeroActions
import com.ivandsky.puzzlebox.resultui.ResultHeroFacts
import com.ivandsky.puzzlebox.resultui.ResultHeroScreen
import com.ivandsky.puzzlebox.resultui.ResultPrimaryAction
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A past day's first solve: the main button reads "Next: Zip · Nov 13" and continues the same game
 * on its next unsolved day, in place of Done. Phone and tablet, light and dark. Goldens in `snapshots/results-hero/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ResultArchiveNextGoldenScreenshotsPhone : ResultArchiveNextGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ResultArchiveNextGoldenScreenshotsTablet : ResultArchiveNextGoldenScreenshots("tablet")

abstract class ResultArchiveNextGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `next day light`() = capture(dark = false)
    @Test fun `next day dark`() = capture(dark = true)

    private fun capture(dark: Boolean) {
        rule.showGolden(dark, animated = true) {
            UnderUpBar { Page(NEXT_DAY) }
        }
        rule.settleAndCapture("results-hero", "archive-next", device, dark)
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
        /** A first solve of a past day: the game-wide wording, and the button that continues on 13 November. */
        val NEXT_DAY = facts(
            verdict = RecordVerdict.FirstSolve, previous = null, earlier = emptyList(), streak = 1,
            rank = ResultRank.Hidden, doneToday = 1, game = "Zip", playAgain = true,
            primary = ResultPrimaryAction.NextDay(GameId("zip"), LocalDate.of(2026, 11, 13), "Zip"),
        ).copy(samePuzzle = SamePuzzleSolves.None, dateLine = "Tue 10 Nov")
    }
}

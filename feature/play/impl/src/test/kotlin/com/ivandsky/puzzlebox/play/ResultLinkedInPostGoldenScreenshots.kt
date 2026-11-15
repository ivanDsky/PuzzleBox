package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.LinkedInPostButtonState
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultHeroActions
import com.ivandsky.puzzlebox.gameui.ResultHeroFacts
import com.ivandsky.puzzlebox.gameui.ResultHeroScreen
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.friend.UnderUpBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The result page with the Post to LinkedIn button in each of its eight states, phone, light and dark, animations
 * off. The page without the button is the `results-hero` set, which this change leaves as it was. Goldens in
 * `snapshots/results-linkedin-post/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ResultLinkedInPostGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `post light`() = capture(LinkedInPostButtonState.Post, "post", dark = false)
    @Test fun `post dark`() = capture(LinkedInPostButtonState.Post, "post", dark = true)

    @Test fun `posting light`() = capture(LinkedInPostButtonState.Posting, "posting", dark = false)
    @Test fun `posting dark`() = capture(LinkedInPostButtonState.Posting, "posting", dark = true)

    @Test fun `posted light`() = capture(LinkedInPostButtonState.Posted, "posted", dark = false)
    @Test fun `posted dark`() = capture(LinkedInPostButtonState.Posted, "posted", dark = true)

    @Test fun `already on LinkedIn light`() = capture(LinkedInPostButtonState.AlreadyOnLinkedIn, "already-on-linkedin", dark = false)
    @Test fun `already on LinkedIn dark`() = capture(LinkedInPostButtonState.AlreadyOnLinkedIn, "already-on-linkedin", dark = true)

    @Test fun `check light`() = capture(LinkedInPostButtonState.Check, "check", dark = false)
    @Test fun `check dark`() = capture(LinkedInPostButtonState.Check, "check", dark = true)

    @Test fun `retry light`() = capture(LinkedInPostButtonState.Retry, "retry", dark = false)
    @Test fun `retry dark`() = capture(LinkedInPostButtonState.Retry, "retry", dark = true)

    @Test fun `paused light`() = capture(LinkedInPostButtonState.Paused, "paused", dark = false)
    @Test fun `paused dark`() = capture(LinkedInPostButtonState.Paused, "paused", dark = true)

    @Test fun `not today light`() = capture(LinkedInPostButtonState.NotToday, "not-today", dark = false)
    @Test fun `not today dark`() = capture(LinkedInPostButtonState.NotToday, "not-today", dark = true)

    private fun capture(state: LinkedInPostButtonState, name: String, dark: Boolean) {
        rule.showGolden(dark) {
            UnderUpBar {
                ResultHeroScreen(
                    facts = FACTS,
                    onSignIn = {},
                    modifier = Modifier.weight(1f),
                    brand = BRAND,
                    actions = {
                        ResultHeroActions(
                            FACTS.primary,
                            onPrimary = {},
                            onShare = {},
                            onPlayAgain = {},
                            linkedInPost = state,
                            onLinkedInPost = {},
                        )
                    },
                )
            }
        }
        rule.settleAndCapture("results-linkedin-post", name, "phone", dark)
    }

    private companion object {
        val BRAND = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)

        /** The stats cards are left unread (null insights, hidden rank), so the frame holds the time, the verdict and the actions. */
        val FACTS = ResultHeroFacts(
            timeMs = 58_000L,
            hintsUsed = 0,
            verdict = RecordVerdict.NewBest,
            previousBest = BestScore(65_000L, 0),
            rankOrder = RankOrder.TimeThenHints,
            game = "Queens",
            puzzleNumber = 918,
            practice = false,
            playAgain = false,
            dateLine = "Today · Sat 7 Nov",
            doneToday = 2,
            total = 3,
            insights = null,
            rank = ResultRank.Hidden,
            primary = ResultPrimaryAction.Next(GameId("tango"), "Tango"),
        )
    }
}

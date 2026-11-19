package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.friend.UnderUpBar
import com.ivandsky.puzzlebox.resultui.LinkedInPostRowState
import com.ivandsky.puzzlebox.resultui.ResultBelowCardRows
import com.ivandsky.puzzlebox.resultui.ResultHeroActions
import com.ivandsky.puzzlebox.resultui.ResultHeroFacts
import com.ivandsky.puzzlebox.resultui.ResultHeroScreen
import com.ivandsky.puzzlebox.resultui.ResultPrimaryAction
import com.ivandsky.puzzlebox.resultui.replay.WatchReplayRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The result page with the Post to LinkedIn row under the hero card, beside Watch replay on the tablet and above it on
 * the phone, in each of its eight states, light and dark, animations off. The page without the row is the
 * `results-hero` set, which this change leaves as it was. Goldens in `snapshots/results-linkedin-post/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ResultLinkedInPostGoldenScreenshotsPhone : ResultLinkedInPostGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ResultLinkedInPostGoldenScreenshotsTablet : ResultLinkedInPostGoldenScreenshots("tablet")

abstract class ResultLinkedInPostGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `post light`() = capture(LinkedInPostRowState.Post, "post", dark = false)
    @Test fun `post dark`() = capture(LinkedInPostRowState.Post, "post", dark = true)

    @Test fun `posting light`() = capture(LinkedInPostRowState.Posting, "posting", dark = false)
    @Test fun `posting dark`() = capture(LinkedInPostRowState.Posting, "posting", dark = true)

    @Test fun `posted light`() = capture(LinkedInPostRowState.Posted, "posted", dark = false)
    @Test fun `posted dark`() = capture(LinkedInPostRowState.Posted, "posted", dark = true)

    @Test fun `already on LinkedIn light`() = capture(LinkedInPostRowState.AlreadyOnLinkedIn, "already-on-linkedin", dark = false)
    @Test fun `already on LinkedIn dark`() = capture(LinkedInPostRowState.AlreadyOnLinkedIn, "already-on-linkedin", dark = true)

    @Test fun `check light`() = capture(LinkedInPostRowState.Check, "check", dark = false)
    @Test fun `check dark`() = capture(LinkedInPostRowState.Check, "check", dark = true)

    @Test fun `retry light`() = capture(LinkedInPostRowState.Retry, "retry", dark = false)
    @Test fun `retry dark`() = capture(LinkedInPostRowState.Retry, "retry", dark = true)

    @Test fun `paused light`() = capture(LinkedInPostRowState.Paused, "paused", dark = false)
    @Test fun `paused dark`() = capture(LinkedInPostRowState.Paused, "paused", dark = true)

    @Test fun `not today light`() = capture(LinkedInPostRowState.NotToday, "not-today", dark = false)
    @Test fun `not today dark`() = capture(LinkedInPostRowState.NotToday, "not-today", dark = true)

    @Test fun `tutorial light`() = capture(LinkedInPostRowState.Tutorial, "tutorial", dark = false)
    @Test fun `tutorial dark`() = capture(LinkedInPostRowState.Tutorial, "tutorial", dark = true)

    private fun capture(state: LinkedInPostRowState, name: String, dark: Boolean) {
        // The not-today row says the puzzle is no longer today's, so its card shows a past date, not "Today".
        val facts = if (state == LinkedInPostRowState.NotToday) FACTS.copy(dateLine = "Fri 6 Nov") else FACTS
        rule.showGolden(dark) {
            UnderUpBar {
                ResultHeroScreen(
                    facts = facts,
                    onSignIn = {},
                    modifier = Modifier.weight(1f),
                    brand = BRAND,
                    actions = { ResultHeroActions(facts.primary, onPrimary = {}, onShare = {}, onPlayAgain = {}) },
                    replay = { columnGap ->
                        ResultBelowCardRows(
                            post = state,
                            time = formatDuration(facts.timeMs),
                            onPost = {},
                            columnGap = columnGap,
                            replay = { hairline ->
                                WatchReplayRow(moves = 31, durationMs = facts.timeMs, onClick = {}, hairline = hairline)
                            },
                        )
                    },
                )
            }
        }
        rule.settleAndCapture("results-linkedin-post", name, device, dark)
    }

    private companion object {
        val BRAND = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)

        /** The streak and time spread are left unread (null insights); the rank gives the page its side card, so the tablet is two columns. */
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
            rank = ResultRank.Ranked(position = 14, count = 212, topPercent = 7),
            primary = ResultPrimaryAction.Next(GameId("tango"), "Tango"),
            moments = emptyList(),
            challenge = null,
            samePuzzle = null,
        )
    }
}

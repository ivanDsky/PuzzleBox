package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
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
 * The Results hero page with a moment card (three rows, the fourth cut) and with each main-button case: beat a
 * friend (with and without the Challenge row under another main button), a missed day, a friend's puzzle and a new
 * community puzzle, phone and tablet, light and dark, after the entrance has settled. Goldens in `snapshots/results-moments/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ResultMomentGoldenScreenshotsPhone : ResultMomentGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ResultMomentGoldenScreenshotsTablet : ResultMomentGoldenScreenshots("tablet")

abstract class ResultMomentGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `moments light`() = capture("moments", MOMENTS, dark = false)
    @Test fun `moments dark`() = capture("moments", MOMENTS, dark = true)

    @Test fun `full set light`() = capture("full-set", FULL_SET, dark = false)
    @Test fun `full set dark`() = capture("full-set", FULL_SET, dark = true)

    @Test fun `challenge row light`() = capture("challenge-row", CHALLENGE_ROW, dark = false)
    @Test fun `challenge row dark`() = capture("challenge-row", CHALLENGE_ROW, dark = true)

    @Test fun `challenge main light`() = capture("challenge-main", CHALLENGE_MAIN, dark = false)
    @Test fun `challenge main dark`() = capture("challenge-main", CHALLENGE_MAIN, dark = true)

    @Test fun `missed day light`() = capture("missed-day", MISSED_DAY, dark = false)
    @Test fun `missed day dark`() = capture("missed-day", MISSED_DAY, dark = true)

    @Test fun `friend puzzle light`() = capture("friend-puzzle", FRIEND_PUZZLE, dark = false)
    @Test fun `friend puzzle dark`() = capture("friend-puzzle", FRIEND_PUZZLE, dark = true)

    @Test fun `community puzzle light`() = capture("community-puzzle", COMMUNITY_PUZZLE, dark = false)
    @Test fun `community puzzle dark`() = capture("community-puzzle", COMMUNITY_PUZZLE, dark = true)

    private fun capture(name: String, facts: ResultHeroFacts, dark: Boolean) {
        rule.showGolden(dark) { UnderUpBar { Page(facts) } }
        rule.settleAndCapture("results-moments", name, device, dark)
    }

    @Composable
    private fun ColumnScope.Page(facts: ResultHeroFacts) {
        ResultHeroScreen(
            facts = facts,
            onSignIn = {},
            modifier = Modifier.weight(1f),
            brand = BRAND,
            actions = {
                ResultHeroActions(
                    facts.primary,
                    onPrimary = {},
                    onShare = {},
                    onPlayAgain = {},
                    challenge = facts.challenge,
                    onChallenge = {},
                )
            },
        )
    }

    private companion object {
        val BRAND = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)
        val MAYA = ResultPrimaryAction.ChallengeFriend("u1", "Maya")
        val CODE = ShareCode.parse("ABC123")!!

        val NEW_BEST = facts(verdict = RecordVerdict.NewBest, timeMs = 58_000L, previous = BestScore(65_000L, 0))

        val MOMENTS = NEW_BEST.copy(
            moments = listOf(Moment.Streak(7), Moment.FullSetDone(), Moment.TopThreeFriends(2), Moment.PersonalBest),
        )
        val FULL_SET = NEW_BEST.copy(moments = listOf(Moment.FullSetDone(Moment.FullSetDone.DayTotal(timeMs = 312_000L, hints = 2))))
        val CHALLENGE_ROW = NEW_BEST.copy(moments = listOf(Moment.PassedFriend("u1", "Maya")), challenge = MAYA)
        val CHALLENGE_MAIN = NEW_BEST.copy(primary = MAYA, challenge = MAYA)
        val MISSED_DAY = NEW_BEST.copy(primary = ResultPrimaryAction.MissedDay(GameId("tango"), LocalDate.of(2026, 11, 3), "Tango"))
        val FRIEND_PUZZLE = NEW_BEST.copy(primary = ResultPrimaryAction.FriendPuzzle(CODE, GameId("tango"), "Maya"))
        val COMMUNITY_PUZZLE = NEW_BEST.copy(primary = ResultPrimaryAction.CommunityPuzzle(CODE, GameId("tango"), "Sam Okafor"))
    }
}

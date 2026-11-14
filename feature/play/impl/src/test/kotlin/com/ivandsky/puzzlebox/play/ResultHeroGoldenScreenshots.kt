package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.domain.ResultDay
import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.StreakFreezes
import com.ivandsky.puzzlebox.domain.WeekdaySpread
import com.ivandsky.puzzlebox.domain.timeSpread
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultHeroActions
import com.ivandsky.puzzlebox.gameui.ResultHeroFacts
import com.ivandsky.puzzlebox.gameui.ResultHeroScreen
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.friend.UnderUpBar
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Results hero page under its Up bar, phone and tablet, light and dark, after the entrance
 * has settled (animations on, so the count-up and the beats have run): a plain solve, a first
 * solve, hints used, a new best, a signed-out player, an hour-plus time with twelve hints, an
 * all-done day, a Strands (hints-first) solve, a play again, the weekday scope and sixty solves.
 * One more frame takes the new best with animations off (reduced motion). The all-done frame leaves the rollover unread, so its
 * countdown line, which follows the wall clock, never moves a pixel. Goldens in `snapshots/results-hero/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ResultHeroGoldenScreenshotsPhone : ResultHeroGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ResultHeroGoldenScreenshotsTablet : ResultHeroGoldenScreenshots("tablet")

abstract class ResultHeroGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `default light`() = capture("default", DEFAULT, dark = false)
    @Test fun `default dark`() = capture("default", DEFAULT, dark = true)

    @Test fun `first solve light`() = capture("first-solve", FIRST_SOLVE, dark = false)
    @Test fun `first solve dark`() = capture("first-solve", FIRST_SOLVE, dark = true)

    @Test fun `hints light`() = capture("hints", HINTS, dark = false)
    @Test fun `hints dark`() = capture("hints", HINTS, dark = true)

    @Test fun `new best light`() = capture("new-best", NEW_BEST, dark = false)
    @Test fun `new best dark`() = capture("new-best", NEW_BEST, dark = true)

    @Test fun `signed out light`() = capture("signed-out", SIGNED_OUT, dark = false)
    @Test fun `signed out dark`() = capture("signed-out", SIGNED_OUT, dark = true)

    @Test fun `long light`() = capture("long", LONG, dark = false)
    @Test fun `long dark`() = capture("long", LONG, dark = true)

    @Test fun `all done light`() = capture("all-done", ALL_DONE, dark = false)
    @Test fun `all done dark`() = capture("all-done", ALL_DONE, dark = true)

    @Test fun `strands light`() = capture("strands", STRANDS, dark = false, brand = STRANDS_BRAND)
    @Test fun `strands dark`() = capture("strands", STRANDS, dark = true, brand = STRANDS_BRAND)

    @Test fun `play again light`() = capture("play-again", PLAY_AGAIN, dark = false)
    @Test fun `play again dark`() = capture("play-again", PLAY_AGAIN, dark = true)

    @Test fun `weekday light`() = capture("weekday", WEEKDAY, dark = false)
    @Test fun `weekday dark`() = capture("weekday", WEEKDAY, dark = true)

    @Test fun `sixty solves light`() = capture("sixty-solves", SIXTY, dark = false)
    @Test fun `sixty solves dark`() = capture("sixty-solves", SIXTY, dark = true)

    @Test fun `freeze used light`() = capture("freeze-used", FREEZE_USED, dark = false)
    @Test fun `freeze used dark`() = capture("freeze-used", FREEZE_USED, dark = true)

    @Test fun `new best reduced motion light`() = capture("new-best-reduced", NEW_BEST, dark = false, animated = false)
    @Test fun `new best reduced motion dark`() = capture("new-best-reduced", NEW_BEST, dark = true, animated = false)

    private fun capture(
        name: String,
        facts: ResultHeroFacts,
        dark: Boolean,
        brand: GameBrand = BRAND,
        animated: Boolean = true,
    ) {
        rule.showGolden(dark, animated = animated) {
            UnderUpBar { Page(facts, brand) }
        }
        rule.settleAndCapture("results-hero", name, device, dark)
    }

    @Composable
    private fun ColumnScope.Page(facts: ResultHeroFacts, brand: GameBrand) {
        ResultHeroScreen(
            facts = facts,
            onSignIn = {},
            modifier = Modifier.weight(1f),
            brand = brand,
            actions = { ResultHeroActions(facts.primary, onPrimary = {}, onShare = {}, onPlayAgain = {}) },
        )
    }

    private companion object {
        val BRAND = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)
        val STRANDS_BRAND = GameBrand(AccentSlot.Teal, GameGlyphs.Queens)

        /** A week ending Saturday 7 Nov whose last [streak] days are solved. */
        val TODAY: LocalDate = LocalDate.of(2026, 11, 7)

        val EARLIER = listOf(112_000L, 90_000L, 104_000L, 98_000L, 121_000L).map { BestScore(it, 0) }
        val EARLIER_LONG = listOf(3_000_000L, 3_400_000L, 3_650_000L, 3_100_000L).map { BestScore(it, 3) }
        val SPREAD_12 = List(11) { BestScore(70_000L + (it * 37 % 90) * 1000L, 0) }
        val SPREAD_60 = List(59) { BestScore(70_000L + (it * 37 % 90) * 1000L, 0) }

        fun insights(
            current: BestScore,
            earlier: List<BestScore> = EARLIER,
            order: RankOrder = RankOrder.TimeThenHints,
            streak: Int = 3,
            weekdayEarlier: List<BestScore> = earlier.take(1),
            freezes: StreakFreezes? = null,
            moment: StreakFreezes.Moment? = null,
        ) = ResultInsights(
            streak = streak,
            week = (6 downTo 0).map { back ->
                val day = TODAY.minusDays(back.toLong())
                val frozen = freezes?.frozenDays?.contains(day) == true
                ResultDay(day, solved = back < streak.coerceAtMost(7) && !frozen, today = back == 0, frozen = frozen)
            },
            spread = timeSpread(earlier, current, order),
            weekday = WeekdaySpread(TODAY.dayOfWeek, timeSpread(weekdayEarlier, current, order), fastest = false),
            freezes = freezes,
            moment = moment,
        )

        fun facts(
            verdict: RecordVerdict? = RecordVerdict.NotBest,
            timeMs: Long = 100_000L,
            hints: Int = 0,
            previous: BestScore? = BestScore(90_000L, 0),
            earlier: List<BestScore> = EARLIER,
            order: RankOrder = RankOrder.TimeThenHints,
            game: String = "Queens",
            playAgain: Boolean = false,
            streak: Int = 3,
            rank: ResultRank = ResultRank.Ranked(position = 14, count = 212, topPercent = 7),
            doneToday: Int = 2,
            primary: ResultPrimaryAction = ResultPrimaryAction.Next(GameId("tango"), "Tango"),
            weekdayEarlier: List<BestScore> = earlier.take(1),
            freezes: StreakFreezes? = null,
            moment: StreakFreezes.Moment? = null,
        ) = ResultHeroFacts(
            timeMs = timeMs,
            hintsUsed = hints,
            verdict = verdict,
            previousBest = previous,
            rankOrder = order,
            game = game,
            puzzleNumber = 918,
            practice = false,
            playAgain = playAgain,
            dateLine = "Today · Sat 7 Nov",
            doneToday = doneToday,
            total = 3,
            insights = insights(BestScore(timeMs, hints), earlier, order, streak, weekdayEarlier, freezes, moment),
            rank = rank,
            primary = primary,
        )

        val DEFAULT = facts()
        val FIRST_SOLVE = facts(
            verdict = RecordVerdict.FirstSolve, previous = null, earlier = emptyList(), streak = 1,
            rank = ResultRank.Hidden, doneToday = 1,
        )
        val HINTS = facts(timeMs = 151_000L, hints = 2)
        val NEW_BEST = facts(verdict = RecordVerdict.NewBest, timeMs = 58_000L, previous = BestScore(65_000L, 0))
        val SIGNED_OUT = facts(rank = ResultRank.SignedOut)
        val LONG = facts(
            timeMs = 3_923_000L, hints = 12, game = "Mini Sudoku", previous = BestScore(3_000_000L, 3),
            earlier = EARLIER_LONG, rank = ResultRank.Ranked(position = 1_204, count = 5_612, topPercent = 22),
        )
        val ALL_DONE = facts(doneToday = 3, primary = ResultPrimaryAction.AllDone)
        val STRANDS = facts(
            verdict = RecordVerdict.NewBest, order = RankOrder.HintsThenTime, game = "Strands",
            timeMs = 142_000L, hints = 0, previous = BestScore(150_000L, 2),
            earlier = listOf(BestScore(150_000L, 2), BestScore(131_000L, 1), BestScore(170_000L, 3)),
        )
        val PLAY_AGAIN = facts(
            verdict = RecordVerdict.PlayAgain, timeMs = 131_000L, hints = 2, previous = BestScore(118_000L, 0),
            playAgain = true, rank = ResultRank.Hidden, primary = ResultPrimaryAction.Done,
        )

        /** The weekday scope opens: three earlier Saturdays among twelve solves. */
        val WEEKDAY = facts(
            timeMs = 84_000L, earlier = SPREAD_12, weekdayEarlier = listOf(BestScore(90_000L, 0), BestScore(98_000L, 0), BestScore(104_000L, 0)),
        )

        /** Thursday was missed and held by the one freeze spent; two are left once today's solve counts. */
        val FREEZE_USED = facts(
            streak = 5,
            freezes = StreakFreezes(
                held = 2,
                frozenDays = setOf(TODAY.minusDays(2)),
                daysToNext = 4,
                lastSolve = null,
            ),
            moment = StreakFreezes.Moment(TODAY, spent = listOf(TODAY.minusDays(2)), earned = false),
        )
        val SIXTY = facts(timeMs = 100_000L, earlier = SPREAD_60, weekdayEarlier = emptyList())
    }
}

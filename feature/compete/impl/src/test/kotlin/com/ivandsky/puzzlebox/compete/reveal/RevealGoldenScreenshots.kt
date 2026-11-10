package com.ivandsky.puzzlebox.compete.reveal

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.MatchVerdict
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val GOLDEN_PHONE = "w411dp-h891dp-420dpi"
private const val GOLDEN_TABLET = "w1280dp-h800dp-land-mdpi"
private const val SETTLE_MS = 2_000L

/**
 * The whole reveal, phone and tablet, light and dark: the VS intro, a race mid-run, a Strands card, a no-result card,
 * a tie card, a long-name intro, the summary after a win, a loss, a draw and a best of 7, and the Rematch sheet.
 * Every frame is the machine advanced to a known instant. Goldens in `snapshots/reveal/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class RevealPhoneGoldenScreenshots : RevealGoldenScreenshots("phone", wide = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class RevealTabletGoldenScreenshots : RevealGoldenScreenshots("tablet", wide = true)

abstract class RevealGoldenScreenshots(private val device: String, private val wide: Boolean) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `intro light`() = stage("intro", PLAN, at = 0, dark = false)
    @Test fun `intro dark`() = stage("intro", PLAN, at = 0, dark = true)

    @Test fun `race light`() = stage("race", PLAN, at = RACE_MID, dark = false)
    @Test fun `race dark`() = stage("race", PLAN, at = RACE_MID, dark = true)

    @Test fun `strands light`() = stage("strands", PLAN, at = STRANDS_MID, dark = false)
    @Test fun `strands dark`() = stage("strands", PLAN, at = STRANDS_MID, dark = true)

    @Test fun `no result light`() = stage("no-result", PLAN, at = NO_RESULT_END, dark = false)
    @Test fun `no result dark`() = stage("no-result", PLAN, at = NO_RESULT_END, dark = true)

    @Test fun `tie light`() = stage("tie", PLAN, at = TIE_END, dark = false)
    @Test fun `tie dark`() = stage("tie", PLAN, at = TIE_END, dark = true)

    @Test fun `long name intro light`() = stage("intro-long-name", LONG_NAMES, at = 0, dark = false)
    @Test fun `long name intro dark`() = stage("intro-long-name", LONG_NAMES, at = 0, dark = true)

    @Test fun `summary win light`() = summary("summary-win", WIN, dark = false)
    @Test fun `summary win dark`() = summary("summary-win", WIN, dark = true)

    @Test fun `summary loss light`() = summary("summary-loss", LOSS, dark = false)
    @Test fun `summary loss dark`() = summary("summary-loss", LOSS, dark = true)

    @Test fun `summary draw light`() = summary("summary-draw", DRAW, dark = false)
    @Test fun `summary draw dark`() = summary("summary-draw", DRAW, dark = true)

    @Test fun `summary best of 7 light`() = summary("summary-best-of-7", BEST_OF_7, dark = false)
    @Test fun `summary best of 7 dark`() = summary("summary-best-of-7", BEST_OF_7, dark = true)

    @Test fun `rematch sheet light`() = rematch(dark = false)
    @Test fun `rematch sheet dark`() = rematch(dark = true)

    private fun stage(name: String, plan: RevealPlan, at: Long, dark: Boolean) {
        val machine = RevealMachine(plan.legs, reduced = false).apply { advance(at) }
        val state = machine.state.value
        rule.showGolden(dark) {
            RevealStage(plan, state, GamePlayRegistry(emptyList()), wide, onSkip = {})
        }
        rule.settleAndCapture(name, device, dark)
    }

    private fun summary(name: String, summary: DuelSummary, dark: Boolean) {
        rule.showGolden(dark) {
            RevealSummary(summary, GamePlayRegistry(emptyList()), wide, onBack = {}, onRematch = {}, onWatchAgain = {})
        }
        rule.settleAndCapture(name, device, dark)
    }

    private fun rematch(dark: Boolean) {
        rule.showGolden(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                RematchContent(opponent = "Sam", count = 3, sending = false, note = null, onSend = {}, onDismiss = {})
            }
        }
        rule.settleAndCapture("rematch-sheet", device, dark)
    }

    private companion object {
        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        val CREATED_AT: Instant = Instant.parse("2026-11-09T10:00:00Z")
        val ZIP = GameId("zip")
        val QUEENS = GameId("queens")
        val STRANDS = GameId("strands")

        fun time(ms: Long, hints: Int = 0) = DuelSideState.Time(ms, hints)

        fun card(game: GameId, title: String, mine: DuelSideState, theirs: DuelSideState, verdict: LegVerdict, basis: String, pill: String) =
            RevealCard(LegReveal(game, mine, theirs, verdict), title, RankOrder.TimeThenHints, basis, pill)

        val ZIP_WIN = card(ZIP, "Zip #401", time(58_000), time(72_000), LegVerdict.Win(LegBasis.Time), "14 s faster", "Ivan wins")
        val QUEENS_LOSS = card(QUEENS, "Queens #88", time(95_000), time(80_000), LegVerdict.Loss(LegBasis.Time), "15 s faster", "Sam wins")
        val STRANDS_WIN = RevealCard(
            LegReveal(STRANDS, time(120_000, 0), time(100_000, 2), LegVerdict.Win(LegBasis.Hints)),
            "Strands #12", RankOrder.HintsThenTime, "Fewer hints · 0 vs 2", "Ivan wins",
        )
        val NO_RESULT = card(
            QUEENS, "Queens #89", DuelSideState.NotPlayed, time(60_000), LegVerdict.Loss(LegBasis.Time),
            "You didn't play · Sam wins this puzzle", "Sam wins",
        )
        val TIE = card(ZIP, "Zip #402", time(61_000), time(61_400), LegVerdict.Draw, "Same whole second · no point", "Tie")

        fun plan(me: String, them: String, vararg cards: RevealCard) =
            RevealPlan(Player("ivan", me, null), Player("sam", them, null), cards.toList())

        val PLAN = plan("Ivan", "Sam", ZIP_WIN, STRANDS_WIN, NO_RESULT, TIE)
        val LONG_NAMES = plan("Alexandrina Konstantinopolska", "Bartholomew Featherstonehaugh", ZIP_WIN, QUEENS_LOSS, TIE)

        const val RACE_MID = RevealMachine.IntroMs + 1_500L
        const val STRANDS_MID = RevealMachine.IntroMs + RevealMachine.LegMs + 1_500L
        const val NO_RESULT_END = RevealMachine.IntroMs + 2 * RevealMachine.LegMs + 2_600L
        const val TIE_END = RevealMachine.IntroMs + 3 * RevealMachine.LegMs + 2_700L

        fun summaryOf(plan: RevealPlan, verdict: MatchVerdict): DuelSummary {
            val verdicts = plan.legs.map { it.verdict }
            val wins = verdicts.count { it is LegVerdict.Win }
            val losses = verdicts.count { it is LegVerdict.Loss }
            check(verdict == if (wins > losses) MatchVerdict.Won else if (wins < losses) MatchVerdict.Lost else MatchVerdict.Drawn) {
                "fixture verdict $verdict does not match $wins wins to $losses losses"
            }
            val match = Match(
                code = CODE,
                formatVersion = Match.FORMAT_VERSION,
                createdAt = CREATED_AT,
                deadlineHours = 24,
                challenger = plan.me,
                invitedUid = null,
                opponent = plan.opponent,
                legs = plan.legs.mapIndexed { i, leg -> MatchLeg(LegRef.Daily(leg.gameId, LocalDate.of(2026, 11, 9), 400 + i), authorUid = null) },
                includeFriends = false,
                cancelled = false,
                declined = false,
                rematchOf = null,
                progress = emptyMap(),
            )
            return duelSummary(match, plan, Outcome(verdicts, wins, losses, true, verdict), ZoneOffset.UTC, Locale.US)
        }

        val WIN = summaryOf(plan("Ivan", "Sam", ZIP_WIN, STRANDS_WIN, QUEENS_LOSS), MatchVerdict.Won)
        val LOSS = summaryOf(plan("Ivan", "Sam", QUEENS_LOSS, NO_RESULT, ZIP_WIN), MatchVerdict.Lost)
        val DRAW = summaryOf(plan("Ivan", "Sam", ZIP_WIN, QUEENS_LOSS, TIE), MatchVerdict.Drawn)
        // 4 wins, 2 losses (one a no-result) and a tie: a won best of 7; the tie scores for neither side.
        val BEST_OF_7 = summaryOf(
            plan("Ivan", "Sam", ZIP_WIN, QUEENS_LOSS, STRANDS_WIN, NO_RESULT, TIE, ZIP_WIN, STRANDS_WIN),
            MatchVerdict.Won,
        )
    }
}

/**
 * Shows [content] the way the app's root Scaffold does in the [dark] or light theme, system animations off
 * (reduced motion), the compose clock frozen until [settleAndCapture].
 */
private fun ComposeContentTestRule.showGolden(dark: Boolean, content: @Composable () -> Unit) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    mainClock.autoAdvance = false
    setContent {
        val config = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides config) {
            PuzzleBoxTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }
}

/**
 * Lets entrances settle, then captures the whole screen to `snapshots/reveal/<name>-<device>-<light|dark>.png`.
 * Recorded with `-Proborazzi.record=true`, verified with `-Proborazzi.verify=true`.
 */
@OptIn(ExperimentalRoborazziApi::class)
private fun ComposeContentTestRule.settleAndCapture(name: String, device: String, dark: Boolean) {
    mainClock.advanceTimeBy(SETTLE_MS)
    val file = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("snapshots/reveal/$name-$device-${if (dark) "dark" else "light"}.png")
        .canonicalFile
        .also { checkNotNull(it.parentFile).mkdirs() }
    captureScreenRoboImage(file.absolutePath)
}

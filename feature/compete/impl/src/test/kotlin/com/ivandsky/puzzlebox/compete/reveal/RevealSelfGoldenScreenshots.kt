package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.MatchVerdict
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.resultui.DuelSideState
import com.ivandsky.puzzlebox.resultui.replay.CompareLayout
import com.ivandsky.puzzlebox.resultui.replay.CompareNames
import com.ivandsky.puzzlebox.resultui.replay.ReplayComparePage
import com.ivandsky.puzzlebox.resultui.replay.ReplayTags
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
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
 * The reveal of a self duel, phone and tablet, light and dark: the intro against Past you, a race frame with the gap to
 * the first solve, the summary after a win, a loss and a tie, the compare of You against Past you, and the summary
 * where one first solve has no replay. Goldens in `snapshots/reveal/` as `reveal-<state>-<device>-<light|dark>.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class RevealSelfPhoneGoldenScreenshots : RevealSelfGoldenScreenshots("phone", wide = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class RevealSelfTabletGoldenScreenshots : RevealSelfGoldenScreenshots("tablet", wide = true)

abstract class RevealSelfGoldenScreenshots(private val device: String, private val wide: Boolean) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `intro light`() = stage("intro", at = 0, dark = false)
    @Test fun `intro dark`() = stage("intro", at = 0, dark = true)

    @Test fun `race light`() = stage("race", at = RACE_MID, dark = false)
    @Test fun `race dark`() = stage("race", at = RACE_MID, dark = true)

    @Test fun `summary win light`() = summary("summary-win", WIN, dark = false)
    @Test fun `summary win dark`() = summary("summary-win", WIN, dark = true)

    @Test fun `summary loss light`() = summary("summary-loss", LOSS, dark = false)
    @Test fun `summary loss dark`() = summary("summary-loss", LOSS, dark = true)

    @Test fun `summary tie light`() = summary("summary-tie", TIE, dark = false)
    @Test fun `summary tie dark`() = summary("summary-tie", TIE, dark = true)

    @Test fun `no replay light`() = summary("no-replay", WIN, dark = false, compares = setOf(0, 2), noReplay = setOf(1))
    @Test fun `no replay dark`() = summary("no-replay", WIN, dark = true, compares = setOf(0, 2), noReplay = setOf(1))

    @Test fun `compare light`() = compare(dark = false)
    @Test fun `compare dark`() = compare(dark = true)

    private fun stage(name: String, at: Long, dark: Boolean) {
        val plan = WIN_PLAN
        val machine = RevealMachine(plan.legs, reduced = false).apply { advance(at) }
        val state = machine.state.value
        rule.showGolden(dark) { RevealStage(plan, state, goldenBrands(), wide, onBack = {}, onSkip = {}) }
        rule.settleAndCapture(name, device, dark)
    }

    private fun summary(name: String, summary: DuelSummary, dark: Boolean, compares: Set<Int> = WIN_COMPARES, noReplay: Set<Int> = emptySet()) {
        rule.showGolden(dark) {
            RevealSummary(
                summary = summary,
                brands = goldenBrands(),
                wide = wide,
                onBack = {},
                onRematch = {},
                onWatchAgain = {},
                compares = compares,
                noReplay = noReplay,
                onCompare = {},
            )
        }
        rule.settleAndCapture(name, device, dark)
    }

    private fun compare(dark: Boolean) {
        val mine = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES, stepMs = 1_000)
        val theirs = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES, stepMs = 1_200)
        rule.showGolden(dark) {
            ReplayComparePage(
                mine = mine,
                theirs = theirs,
                board = ReplayGoldenBoard,
                names = CompareNames(mine = "Ivan", theirs = SelfSeats.PAST_NAME, theirsPast = true),
                layout = if (wide) CompareLayout.Twin else CompareLayout.FlipPeek,
                context = "Duel · Zip #401",
                onBack = {},
                showErrors = true,
                onShowErrorsChange = {},
                finalLead = Lead.Mine,
            )
        }
        // The replay autoplays: capture it at the end state, as the friend compare goldens do.
        rule.onNodeWithTag(ReplayTags.SEEK).performSemanticsAction(SemanticsActions.SetProgress) { it(maxOf(mine.endMs, theirs.endMs).toFloat()) }
        rule.settleAndCapture("compare", device, dark)
    }

    internal companion object {
        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        val CREATED_AT: Instant = Instant.parse("2026-11-09T10:00:00Z")
        val ZIP = GameId("zip")
        val QUEENS = GameId("queens")
        val STRANDS = GameId("strands")
        val WIN_COMPARES = setOf(0, 1, 2)
        /** Past the loser's 2.4 s run: leg 1 settled, so the score reads 1–0 with the pill and the gap line up. */
        const val RACE_MID = RevealMachine.IntroMs + 2_700L

        fun time(ms: Long, hints: Int = 0) = DuelSideState.Time(ms, hints)

        fun card(
            legIndex: Int,
            game: GameId,
            title: String,
            mine: DuelSideState,
            past: DuelSideState,
            verdict: LegVerdict,
            order: RankOrder = RankOrder.TimeThenHints,
        ): RevealCard {
            val gap = improvementOf(mine, past)
            val pill = when (verdict) {
                is LegVerdict.Win -> "You win"
                is LegVerdict.Loss -> "Past you wins"
                else -> "Tie"
            }
            return RevealCard(
                LegReveal(game, mine, past, verdict), title, "#" + title.substringAfter('#'), order,
                gap?.text.orEmpty(), pill, legIndex = legIndex, improvement = gap,
            )
        }

        fun plan(vararg cards: RevealCard) =
            RevealPlan(Player(SelfSeats.ME, "Ivan", null), Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null), cards.toList())

        fun zip(mine: Long, past: Long, verdict: LegVerdict) = card(0, ZIP, "Zip #401", time(mine), time(past), verdict)
        fun strands(mine: Long, past: Long, mineHints: Int, pastHints: Int, verdict: LegVerdict) =
            card(1, STRANDS, "Strands #12", time(mine, mineHints), time(past, pastHints), verdict)
        fun queens(mine: Long, past: Long, verdict: LegVerdict) = card(2, QUEENS, "Queens #88", time(mine), time(past), verdict)

        val WIN_PLAN = plan(
            zip(58_000, 72_000, LegVerdict.Win(LegBasis.Time)),
            strands(90_000, 132_000, 0, 2, LegVerdict.Win(LegBasis.Time)),
            queens(95_000, 80_000, LegVerdict.Loss(LegBasis.Time)),
        )
        val LOSS_PLAN = plan(
            zip(65_000, 58_000, LegVerdict.Loss(LegBasis.Time)),
            strands(108_000, 120_000, 0, 0, LegVerdict.Win(LegBasis.Time)),
            queens(101_000, 80_000, LegVerdict.Loss(LegBasis.Time)),
        )
        val TIE_PLAN = plan(
            zip(58_000, 72_000, LegVerdict.Win(LegBasis.Time)),
            strands(104_000, 104_000, 0, 0, LegVerdict.Draw),
            queens(95_000, 80_000, LegVerdict.Loss(LegBasis.Time)),
        )

        fun summaryOf(plan: RevealPlan, verdict: MatchVerdict): DuelSummary {
            val verdicts = plan.legs.map { it.verdict }
            val wins = verdicts.count { it is LegVerdict.Win }
            val losses = verdicts.count { it is LegVerdict.Loss }
            val match = Match(
                code = CODE,
                formatVersion = Match.FORMAT_VERSION,
                createdAt = CREATED_AT,
                deadlineHours = SelfSeats.NO_DEADLINE_HOURS,
                challenger = plan.me,
                invitedUid = null,
                opponent = plan.opponent,
                legs = plan.legs.mapIndexed { i, leg -> MatchLeg(LegRef.Daily(leg.gameId, LocalDate.of(2026, 11, 9), plan.cards[i].title.substringAfter('#').toInt()), authorUid = null) },
                includeFriends = false,
                cancelled = false,
                declined = false,
                rematchOf = null,
                progress = emptyMap(),
            )
            return duelSummary(match, plan, Outcome(verdicts, wins, losses, true, verdict), ZoneOffset.UTC, Locale.US)
        }

        val WIN = summaryOf(WIN_PLAN, MatchVerdict.Won)
        val LOSS = summaryOf(LOSS_PLAN, MatchVerdict.Lost)
        val TIE = summaryOf(TIE_PLAN, MatchVerdict.Drawn)
    }
}

/** The root Scaffold's look in the [dark] or light theme, animations off, the compose clock frozen until [settleAndCapture]. */
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

/** Lets entrances settle, then captures the screen to `snapshots/reveal/reveal-<name>-<device>-<light|dark>.png`. */
@OptIn(ExperimentalRoborazziApi::class)
private fun ComposeContentTestRule.settleAndCapture(name: String, device: String, dark: Boolean) {
    mainClock.advanceTimeBy(SETTLE_MS)
    val file = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("snapshots/reveal/reveal-$name-$device-${if (dark) "dark" else "light"}.png")
        .canonicalFile
        .also { checkNotNull(it.parentFile).mkdirs() }
    captureScreenRoboImage(file.absolutePath)
}

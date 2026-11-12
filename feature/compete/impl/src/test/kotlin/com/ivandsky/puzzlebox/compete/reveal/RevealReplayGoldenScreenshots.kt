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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.gameui.replay.CompareLayout
import com.ivandsky.puzzlebox.gameui.replay.CompareNames
import com.ivandsky.puzzlebox.gameui.replay.ReplayComparePage
import com.ivandsky.puzzlebox.gameui.replay.ReplayTags
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
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
 * The duel's compare page and the reveal summary's per-leg Compare actions, phone and tablet, light and dark. The compare
 * has five cases (both sides, mine only, theirs only, a gave-up side frozen, both ended with the final line), each short
 * (12 moves) and long (64 moves over about an hour), captured at the finished state. The short both-sides compare is also
 * captured mid-play. Twin on the tablet, FlipPeek on the phone. The summary offers Compare on one leg of three. Goldens in
 * `snapshots/reveal-replay/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class RevealReplayPhoneGoldenScreenshots : RevealReplayGoldenScreenshots("phone", wide = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class RevealReplayTabletGoldenScreenshots : RevealReplayGoldenScreenshots("tablet", wide = true)

abstract class RevealReplayGoldenScreenshots(private val device: String, private val wide: Boolean) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val layout = if (wide) CompareLayout.Twin else CompareLayout.FlipPeek

    @Test fun `compare both short light`() = compare("compare-both-short", both(SHORT), dark = false)
    @Test fun `compare both short dark`() = compare("compare-both-short", both(SHORT), dark = true)
    @Test fun `compare both long light`() = compare("compare-both-long", both(LONG), dark = false)
    @Test fun `compare both long dark`() = compare("compare-both-long", both(LONG), dark = true)
    @Test fun `compare both playing short light`() = playing("compare-both-playing-short", both(SHORT), dark = false)
    @Test fun `compare both playing short dark`() = playing("compare-both-playing-short", both(SHORT), dark = true)

    @Test fun `compare mine only short light`() = compare("compare-mine-only-short", mineOnly(SHORT), dark = false)
    @Test fun `compare mine only short dark`() = compare("compare-mine-only-short", mineOnly(SHORT), dark = true)
    @Test fun `compare mine only long light`() = compare("compare-mine-only-long", mineOnly(LONG), dark = false)
    @Test fun `compare mine only long dark`() = compare("compare-mine-only-long", mineOnly(LONG), dark = true)

    @Test fun `compare theirs only short light`() = compare("compare-theirs-only-short", theirsOnly(SHORT), dark = false)
    @Test fun `compare theirs only short dark`() = compare("compare-theirs-only-short", theirsOnly(SHORT), dark = true)
    @Test fun `compare theirs only long light`() = compare("compare-theirs-only-long", theirsOnly(LONG), dark = false)
    @Test fun `compare theirs only long dark`() = compare("compare-theirs-only-long", theirsOnly(LONG), dark = true)

    @Test fun `compare gave up short light`() = compare("compare-gave-up-short", gaveUp(SHORT), dark = false)
    @Test fun `compare gave up short dark`() = compare("compare-gave-up-short", gaveUp(SHORT), dark = true)
    @Test fun `compare gave up long light`() = compare("compare-gave-up-long", gaveUp(LONG), dark = false)
    @Test fun `compare gave up long dark`() = compare("compare-gave-up-long", gaveUp(LONG), dark = true)

    @Test fun `compare final line short light`() = compare("compare-final-line-short", finalLine(SHORT), dark = false)
    @Test fun `compare final line short dark`() = compare("compare-final-line-short", finalLine(SHORT), dark = true)
    @Test fun `compare final line long light`() = compare("compare-final-line-long", finalLine(LONG), dark = false)
    @Test fun `compare final line long dark`() = compare("compare-final-line-long", finalLine(LONG), dark = true)

    @Test fun `summary compare light`() = summary("summary-compare", dark = false)
    @Test fun `summary compare dark`() = summary("summary-compare", dark = true)

    private fun compare(name: String, case: Case, dark: Boolean, playMs: Long = 0) {
        rule.showGolden(dark) {
            ReplayComparePage(
                mine = case.mine,
                theirs = case.theirs,
                board = ReplayGoldenBoard,
                names = NAMES,
                layout = layout,
                context = CONTEXT,
                onBack = {},
                finalLead = case.finalLead,
            )
        }
        if (playMs > 0) {
            rule.onNodeWithTag(ReplayTags.PLAY).performClick()
            rule.mainClock.advanceTimeBy(playMs)
        }
        rule.settleAndCapture(name, device, dark)
    }

    /** The compare a few seconds into the replay, both boards mid-play, before the frame is captured. */
    private fun playing(name: String, case: Case, dark: Boolean) = compare(name, case, dark, playMs = PLAY_MS)

    private fun summary(name: String, dark: Boolean) {
        rule.showGolden(dark) {
            RevealSummary(
                summary = SUMMARY,
                brands = goldenBrands(),
                wide = wide,
                onBack = {},
                onRematch = {},
                onWatchAgain = {},
                compares = OFFERED,
                onCompare = {},
            )
        }
        rule.settleAndCapture(name, device, dark)
    }

    private companion object {
        const val PLAY_MS = 5_000L
        const val LONG_MOVES = 64
        const val LONG_STEP_MS = 64_000L
        const val LONG_PARTIAL = 48
        const val LONG_QUIT = 32
        const val SHORT_PARTIAL = 9
        const val SHORT_QUIT = 6
        const val CONTEXT = "Duel · Zip #401"
        val NAMES = CompareNames(mine = "You", theirs = "Maya")
        val OFFERED = setOf(0)

        /** One side's replay lengths: [full] the finished solve, [partial] a shorter solve, [quit] a side that gave up. */
        class Lengths(val full: Int, val partial: Int, val quit: Int, val stepMs: Long) {
            fun solved(moves: Int): Timeline = ReplayFixtures.timeline(moves, stepMs = stepMs)
            fun gaveUp(moves: Int): Timeline = ReplayFixtures.timeline(moves, stepMs = stepMs, outcome = ReplayOutcome.GaveUp)
        }

        val SHORT = Lengths(ReplayFixtures.SHORT_MOVES, SHORT_PARTIAL, SHORT_QUIT, stepMs = 1_000)
        val LONG = Lengths(LONG_MOVES, LONG_PARTIAL, LONG_QUIT, LONG_STEP_MS)

        /** What the compare page is given: a side may be missing, and [finalLead] overrides the computed final line. */
        class Case(val mine: Timeline?, val theirs: Timeline?, val finalLead: Lead? = null)

        fun both(l: Lengths) = Case(l.solved(l.full), l.solved(l.partial))
        fun mineOnly(l: Lengths) = Case(l.solved(l.full), null)
        fun theirsOnly(l: Lengths) = Case(null, l.solved(l.partial))
        fun gaveUp(l: Lengths) = Case(l.solved(l.full), l.gaveUp(l.quit))
        fun finalLine(l: Lengths) = Case(l.solved(l.full), l.solved(l.partial), finalLead = Lead.Mine)

        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        val CREATED_AT: Instant = Instant.parse("2026-11-09T10:00:00Z")
        val ZIP = GameId("zip")
        val QUEENS = GameId("queens")
        val STRANDS = GameId("strands")

        fun time(ms: Long, hints: Int = 0) = DuelSideState.Time(ms, hints)

        fun card(
            legIndex: Int,
            game: GameId,
            title: String,
            mine: DuelSideState,
            theirs: DuelSideState,
            verdict: LegVerdict,
            basis: String,
            pill: String,
            order: RankOrder = RankOrder.TimeThenHints,
        ) = RevealCard(LegReveal(game, mine, theirs, verdict), title, "#" + title.substringAfter('#'), order, basis, pill, legIndex = legIndex)

        // Three legs: the summary's Compare is offered on leg 0 only.
        val ZIP_WIN = card(0, ZIP, "Zip #401", time(58_000), time(72_000), LegVerdict.Win(LegBasis.Time), "14 s faster", "Ivan wins")
        val STRANDS_WIN = card(
            1, STRANDS, "Strands #12", time(120_000, 0), time(100_000, 2), LegVerdict.Win(LegBasis.Hints),
            "Fewer hints · 0 vs 2", "Ivan wins", RankOrder.HintsThenTime,
        )
        val QUEENS_LOSS = card(2, QUEENS, "Queens #88", time(95_000), time(80_000), LegVerdict.Loss(LegBasis.Time), "15 s faster", "Sam wins")

        val SUMMARY: DuelSummary = run {
            val plan = RevealPlan(Player("ivan", "Ivan", null), Player("sam", "Sam", null), listOf(ZIP_WIN, STRANDS_WIN, QUEENS_LOSS))
            val verdicts = plan.legs.map { it.verdict }
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
            duelSummary(match, plan, Outcome(verdicts, 2, 1, true, MatchVerdict.Won), ZoneOffset.UTC, Locale.US)
        }
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
 * Lets entrances settle, then captures the whole screen to `snapshots/reveal-replay/<name>-<device>-<light|dark>.png`.
 * Recorded with `-Proborazzi.record=true`, verified with `-Proborazzi.verify=true`.
 */
@OptIn(ExperimentalRoborazziApi::class)
private fun ComposeContentTestRule.settleAndCapture(name: String, device: String, dark: Boolean) {
    mainClock.advanceTimeBy(SETTLE_MS)
    val file = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("snapshots/reveal-replay/$name-$device-${if (dark) "dark" else "light"}.png")
        .canonicalFile
        .also { checkNotNull(it.parentFile).mkdirs() }
    captureScreenRoboImage(file.absolutePath)
}

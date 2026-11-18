package com.ivandsky.puzzlebox.compete.tab

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.compete.duel.CompeteTime
import com.ivandsky.puzzlebox.compete.duel.DuelLegs
import com.ivandsky.puzzlebox.compete.duel.DuelLegsSkeleton
import com.ivandsky.puzzlebox.compete.duel.DuelUiState
import com.ivandsky.puzzlebox.compete.duel.LegsActions
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The tablet's right pane at 880 dp wide (the Games host's 400 dp list beside a 1280 dp window), 800 dp high. */
private const val GOLDEN_RIGHT_PANE = "w880dp-h800dp-mdpi"

/**
 * The Duels pane's duel in the tablet layout, light and dark: a picked duel (the first puzzle solved, the second
 * selected with its Start button in the right column, the third still new) and the two-column skeleton it shows while
 * it loads. Goldens in `snapshots/pane/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_RIGHT_PANE)
class DuelsPaneGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinWithTheTestGames() {
        startKoin {
            modules(
                module {
                    single { TestGames.registry(listOf(TestGames.alpha, TestGames.beta)) }
                    single { GamePlayRegistry(emptyList()) }
                },
            )
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    @Test fun `picked duel light`() = picked(dark = false)
    @Test fun `picked duel dark`() = picked(dark = true)

    @Test fun `loading light`() = loading(dark = false)
    @Test fun `loading dark`() = loading(dark = true)

    private fun picked(dark: Boolean) {
        rule.showGolden(dark) {
            DuelsPaneContent(selected = CODE, onNewDuel = {}, modifier = Modifier.fillMaxSize()) {
                DuelLegs(
                    state = DuelUiState.Participant(view(), emptyMap()),
                    layout = PlayLayout.TwoPane,
                    now = NOW,
                    time = CompeteTime(Clock.fixed(NOW, ZoneOffset.UTC), Locale.US),
                    actions = LegsActions({}, {}, {}, {}),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        rule.settleAndCapture("pane", "duel-picked", "tablet", dark)
    }

    private fun loading(dark: Boolean) {
        rule.showGolden(dark) {
            DuelsPaneContent(selected = CODE, onNewDuel = {}, modifier = Modifier.fillMaxSize()) {
                DuelLegsSkeleton()
            }
        }
        rule.settleAndCapture("pane", "duel-loading", "tablet", dark)
    }

    private fun view(): MatchView {
        val ann = Player("ann", "Ann", null)
        val sam = Player("sam", "Sam", null)
        val legs = List(3) { MatchLeg(LegRef.Daily(if (it == 1) TestGames.beta else TestGames.alpha, LocalDate.of(2026, 11, 9), 400 + it), authorUid = null) }
        val match = Match(
            code = CODE,
            formatVersion = Match.FORMAT_VERSION,
            createdAt = NOW - Duration.ofHours(7),
            deadlineHours = 24,
            challenger = ann,
            invitedUid = null,
            opponent = sam,
            legs = legs,
            includeFriends = false,
            cancelled = false,
            declined = false,
            rematchOf = null,
            progress = mapOf("ann" to setOf(0), "sam" to setOf(0, 1)),
        )
        val done = OutboxResult(CODE, LegResult("ann", 0, 102_000, 1, completedAtMs = 1, gaveUp = false), ResultPostState.Posted, attempts = 0)
        return MatchView(
            match = match,
            me = "ann",
            mine = listOf(done),
            theirs = listOf(LegSeen.Played, LegSeen.Played, LegSeen.NotPlayed),
            outcome = Outcome(emptyList(), 0, 0, settled = false, verdict = null),
            local = null,
        )
    }

    private companion object {
        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        val NOW: Instant = Instant.parse("2026-11-09T12:00:00Z")
    }
}

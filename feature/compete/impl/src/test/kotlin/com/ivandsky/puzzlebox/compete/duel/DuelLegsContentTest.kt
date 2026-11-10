package com.ivandsky.puzzlebox.compete.duel

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
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

/**
 * The puzzles screen's header: the countdown and "score hidden" pills belong to a duel still being played, and are
 * gone once the footer says the reveal is ready. The match is created now, so its deadline lies a day ahead of the
 * screen's own clock; the screen's time is the [now] the test passes in, never the wall clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuelLegsContentTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)

    @Before
    fun startKoinWithTheTestGames() {
        startKoin {
            modules(
                module {
                    single { TestGames.registry(listOf(TestGames.alpha)) }
                    single { GamePlayRegistry(emptyList()) }
                },
            )
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun match(progress: Map<String, Set<Int>>) = Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = CREATED,
        deadlineHours = 24,
        challenger = ann,
        invitedUid = null,
        opponent = bob,
        legs = List(2) { MatchLeg(LegRef.Daily(TestGames.alpha, LocalDate.of(2026, 11, 9), 400 + it), authorUid = null) },
        includeFriends = false,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = progress,
    )

    private fun mine(vararg legs: Int) = legs.map {
        OutboxResult(code, LegResult("ann", it, 102_000, 0, completedAtMs = 1, gaveUp = false), ResultPostState.Posted, attempts = 0)
    }

    private fun show(match: Match, mine: List<OutboxResult>, now: Instant = CREATED) {
        val view = MatchView(
            match = match,
            me = "ann",
            mine = mine,
            theirs = match.legs.map { LegSeen.NotPlayed },
            outcome = Outcome(emptyList(), 0, 0, settled = false, verdict = null),
            local = null,
        )
        rule.setContent {
            PuzzleBoxTheme {
                DuelLegs(
                    state = DuelUiState.Participant(view, emptyMap()),
                    layout = PlayLayout.SinglePane,
                    now = now,
                    time = CompeteTime(Clock.fixed(now, ZoneOffset.UTC), Locale.US),
                    actions = LegsActions({}, {}, {}),
                )
            }
        }
    }

    private fun assertPills(shown: Boolean) {
        val count = if (shown) 1 else 0
        rule.onAllNodesWithTag("duel.legs.timeLeft").assertCountEquals(count)
        rule.onAllNodesWithText("Score hidden until it settles").assertCountEquals(count)
    }

    @Test
    fun theHeaderPillsAreHiddenOnceTheRevealIsReady() {
        show(match(progress = mapOf("bob" to setOf(0, 1))), mine(0, 1))

        rule.onNodeWithTag("duel.legs.reveal").assertExists()
        assertPills(shown = false)
    }

    @Test
    fun theHeaderPillsShowWhileIAmStillPlaying() {
        show(match(progress = emptyMap()), mine(0))

        rule.onAllNodesWithTag("duel.legs.reveal").assertCountEquals(0)
        assertPills(shown = true)
    }

    @Test
    fun theHeaderPillsShowWhileIWaitOnTheOpponent() {
        show(match(progress = mapOf("bob" to setOf(0))), mine(0, 1))

        rule.onNodeWithTag("duel.legs.nudge").assertExists()
        assertPills(shown = true)
    }

    @Test
    fun theCountdownUsesTheInjectedNow() {
        val deadline = CREATED.plus(Duration.ofHours(24))

        show(match(progress = emptyMap()), mine(0), now = deadline.minus(Duration.ofMinutes(90)))
        rule.onNodeWithTag("duel.legs.timeLeft").assertTextEquals("Closes in 1 h 30 m")
    }

    @Test
    fun theCountdownReadsClosedOncePastTheDeadline() {
        show(match(progress = emptyMap()), mine(0), now = CREATED.plus(Duration.ofHours(24)))

        rule.onNodeWithTag("duel.legs.timeLeft").assertTextEquals("Closed")
    }

    @Test
    fun theHeaderEndsInTheScreensZoneAndLocale() {
        show(match(progress = emptyMap()), mine(0))

        // Created Mon 10:00 UTC, 24 hours: the deadline is Tue 10:00 in the test's UTC zone.
        rule.onNodeWithText("Best of 2 · ends Tue 10:00").assertExists()
    }

    @Test
    fun theRouteTickerStartsFromTheProvidedClock() {
        val at = CREATED.plus(Duration.ofHours(3))
        rule.setContent {
            CompositionLocalProvider(LocalCompeteClock provides Clock.fixed(at, ZoneOffset.UTC)) {
                val now by rememberCountdownNow(rememberCompeteTime(), closesAt = CREATED.plus(Duration.ofHours(25)))
                Text(now.toString(), Modifier.testTag("now"))
            }
        }

        rule.onNodeWithTag("now").assertTextEquals(at.toString())
    }

    private companion object {
        val CREATED: Instant = Instant.parse("2026-11-09T10:00:00Z")
    }
}

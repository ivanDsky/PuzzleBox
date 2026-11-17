package com.ivandsky.puzzlebox.compete.duel

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.compete.assertTouchTargetsAtLeast48
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Clock
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
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry

/** The page of a self duel (Start or Resume, See results, Remove duel) and its Remove dialog: every control a 48 dp touch target. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SelfDuelPageTouchTargetsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val code = checkNotNull(ShareCode.parse("ABC234"))

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

    private fun state(doneLegs: Set<Int>): DuelUiState.Participant {
        val legs = List(3) { MatchLeg(LegRef.Daily(TestGames.alpha, LocalDate.of(2026, 11, 9), 400 + it), authorUid = null) }
        val match = Match(
            code = code,
            formatVersion = Match.FORMAT_VERSION,
            createdAt = now,
            deadlineHours = SelfSeats.NO_DEADLINE_HOURS,
            challenger = Player(SelfSeats.ME, "You", null),
            invitedUid = null,
            opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null),
            legs = legs,
            includeFriends = false,
            cancelled = false,
            declined = false,
            rematchOf = null,
            progress = mapOf(SelfSeats.PAST to legs.indices.toSet(), SelfSeats.ME to doneLegs),
        )
        val mine = doneLegs.map {
            OutboxResult(code, LegResult(SelfSeats.ME, it, 60_000, 0, completedAtMs = 1, gaveUp = false), ResultPostState.Posted, 0)
        }
        val view = MatchView(
            match, SelfSeats.ME, mine, legs.map { LegSeen.Played },
            Outcome(emptyList(), 0, 0, settled = false, verdict = null), local = null,
        )
        val firstSolves = legs.associate { it.ref.wire to Instant.parse("2026-09-14T09:00:00Z") }
        return DuelUiState.Participant(view, elapsedMs = if (1 in doneLegs) emptyMap() else mapOf(1 to 32_000L), firstSolvedAt = firstSolves, myName = "Ivan")
    }

    private fun show(state: DuelUiState.Participant, confirm: Boolean = false, layout: PlayLayout = PlayLayout.SinglePane) {
        rule.setContent {
            PuzzleBoxTheme {
                DuelLegs(
                    state = state,
                    layout = layout,
                    now = now,
                    time = CompeteTime(Clock.fixed(now, ZoneOffset.UTC), Locale.US),
                    actions = LegsActions({}, {}, {}, {}),
                    modifier = Modifier,
                )
                if (confirm) RemoveDuelDialog(onRemove = {}, onKeep = {})
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `with a puzzle to play, Start, Resume and Remove duel are 48 dp touch targets`() {
        show(state(doneLegs = setOf(0)))

        rule.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `with every puzzle played, See results and Remove duel are 48 dp touch targets`() {
        show(state(doneLegs = setOf(0, 1, 2)))

        rule.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `the Remove dialog's Keep and Remove are 48 dp touch targets`() {
        show(state(doneLegs = setOf(0)), confirm = true)

        rule.onNodeWithTag("duel.remove.keep").assertExists()
        rule.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `a tablet pane too narrow for two columns keeps one column with its controls as 48 dp touch targets`() {
        show(state(doneLegs = setOf(0)), layout = PlayLayout.TwoPane)

        rule.onNodeWithTag("duel.legs.detail").assertDoesNotExist()
        rule.onNodeWithTag("duel.legs.remove").assertExists()
        rule.assertTouchTargetsAtLeast48()
    }
}

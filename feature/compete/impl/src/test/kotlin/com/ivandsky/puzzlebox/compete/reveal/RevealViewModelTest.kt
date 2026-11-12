package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.LocalMatch
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.domain.RecordRevealSeen
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * The reveal's state holder over a hand-fed match: Ann is the viewer, Bob the opponent, two puzzles. The race is
 * fixed at the first load with a settled puzzle; the summary follows every later load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RevealViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)
    private val createdAt = Instant.parse("2026-11-09T10:00:00Z")
    private val day = LocalDate.of(2026, 11, 9)
    private val registry = TestGames.registry(listOf(TestGames.alpha, TestGames.beta))
    private val match = Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = createdAt,
        deadlineHours = 24,
        challenger = ann,
        invitedUid = null,
        opponent = bob,
        legs = listOf(
            MatchLeg(LegRef.Daily(TestGames.alpha, day, 1), authorUid = null),
            MatchLeg(LegRef.Daily(TestGames.beta, day, 2), authorUid = null),
        ),
        includeFriends = false,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = emptyMap(),
    )
    private val matches = MutableStateFlow<MatchState?>(null)
    private val local = FakeMatchLocalRepository()
    private var marks = 0
    private val counted = object : MatchLocalRepository by local {
        override suspend fun markRevealSeen(code: ShareCode, at: Instant, myScore: Int, theirScore: Int) {
            marks++
            local.markRevealSeen(code, at, myScore, theirScore)
        }
    }

    private fun viewModel() = RevealViewModel(
        code = code.value,
        reducedMotion = false,
        zone = ZoneOffset.UTC,
        locale = Locale.UK,
        observeMatch = { requested -> matches.filterNotNull().also { check(requested == code) } },
        registry = registry,
        brands = GamePlayRegistry(emptyList()),
        recordSeen = RecordRevealSeen(counted) { createdAt.plusSeconds(3_600) },
    )

    private fun TestScope.started(vm: RevealViewModel): RevealViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return vm
    }

    private fun mine(leg: Int, timeMs: Long) =
        OutboxResult(code, LegResult("ann", leg, timeMs, 0, completedAtMs = 1, gaveUp = false), ResultPostState.Posted, attempts = 0)

    private fun theirs(leg: Int, timeMs: Long) = LegSeen.Revealed(LegResult("bob", leg, timeMs, 0, completedAtMs = 1, gaveUp = false))

    /** Both of my times posted; Bob's [revealed] puzzles read, the rest only played; [seenAt] is this device's marker. */
    private fun loaded(revealed: Set<Int>, seenAt: Instant? = null): MatchState {
        val theirs = listOf(0, 1).map { if (it in revealed) theirs(it, 72_000) else LegSeen.Played }
        val verdicts = listOf(0, 1).map { if (it in revealed) LegVerdict.Win(LegBasis.Time) else LegVerdict.Pending }
        val wins = verdicts.count { it is LegVerdict.Win }
        val view = MatchView(
            match = match,
            me = "ann",
            mine = listOf(mine(0, 58_000), mine(1, 61_000)),
            theirs = theirs,
            outcome = Outcome(verdicts, wins, 0, settled = wins == 2, verdict = null),
            local = LocalMatch(code, LocalRole.Participant, "Bob", createdAt, seenAt, dismissed = false),
        )
        return MatchState.Loaded(view)
    }

    private fun running(vm: RevealViewModel) = vm.state.value as RevealUiState.Running

    @Test
    fun aSummaryFollowsAResultThatArrivesAfterTheRevealOpened() = runTest {
        val vm = started(viewModel())
        matches.value = loaded(revealed = setOf(0))
        val opened = running(vm)

        matches.value = loaded(revealed = setOf(0, 1))

        val now = running(vm)
        assertEquals("the summary counts the puzzle settled later", 2, now.summary.myScore)
        assertEquals("the summary lists both puzzles", listOf("Alpha #1", "Beta #2"), now.summary.rows.map { it.title })
        assertSame("the race being watched keeps its cards", opened.race, now.race)
        assertEquals("the race holds only the puzzle settled when it opened", 1, now.race.cards.size)
    }

    @Test
    fun aReEmissionNeverRestartsTheRaceOrMarksTheRevealSeenTwice() = runTest {
        val vm = started(viewModel())
        matches.value = loaded(revealed = setOf(0, 1))
        val opened = running(vm)
        assertFalse("a first opening plays the race", opened.start.startAtEnd)
        assertEquals("a first opening records that it was seen", 1, marks)

        matches.value = loaded(revealed = setOf(0, 1), seenAt = createdAt.plusSeconds(3_600))
        matches.value = MatchState.Offline
        matches.value = loaded(revealed = setOf(0, 1), seenAt = createdAt.plusSeconds(3_600))

        val now = running(vm)
        assertSame("the race is the one that opened", opened.race, now.race)
        assertEquals("the opening still plays from the start", opened.start, now.start)
        assertEquals("no replay was asked for", opened.rerun, now.rerun)
        assertEquals("the marker is written once", 1, marks)
    }

    @Test
    fun anEmptyFirstRunBecomesTheRaceOnceTheResultsArrive() = runTest {
        local.remember(code, LocalRole.Participant, "Bob")
        val vm = started(viewModel())
        matches.value = loaded(revealed = emptySet())
        assertEquals("nothing settled yet", RevealUiState.Unsettled, vm.state.value)
        assertEquals("an empty reveal is not a seen one", 0, marks)

        matches.value = loaded(revealed = setOf(0, 1))

        val now = running(vm)
        assertEquals("the race holds both puzzles", 2, now.race.cards.size)
        assertFalse("the race plays from the start", now.start.startAtEnd)
        assertEquals("the reveal that ran is recorded as seen", 1, marks)
        val stored = local.observeAll().first().single()
        assertEquals("the stamp carries my score", 2, stored.myScore)
        assertEquals("the stamp carries their score", 0, stored.theirScore)
    }

    @Test
    fun watchAgainRacesEveryPuzzleSettledSince() = runTest {
        val vm = started(viewModel())
        matches.value = loaded(revealed = setOf(0))
        matches.value = loaded(revealed = setOf(0, 1))

        vm.watchAgain()

        val again = running(vm)
        assertEquals("the new race holds both puzzles", 2, again.race.cards.size)
        assertEquals("it is the first replay", 1, again.rerun)
        assertEquals("a replay records nothing new", 1, marks)
    }
}
